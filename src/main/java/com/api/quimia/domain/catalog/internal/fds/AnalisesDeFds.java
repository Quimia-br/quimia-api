package com.api.quimia.domain.catalog.internal.fds;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Análise de uma FDS entre o envio do PDF e a confirmação do cadastro. A API não guarda estado: o resultado vai no
 * próprio `analiseId`, assinado com HMAC-SHA-256, e o portal o devolve na confirmação. A assinatura impede alterar o
 * JSON da extração, trocar a URL do PDF ou usar a análise de outra empresa; a validade limita o prazo de uso.
 * Formato: `v1.<payload em base64url>.<assinatura em base64url>`.
 */
@Component
public class AnalisesDeFds {
    /** Análise aberta: o PDF guardado em `pasta` (`url`) e a extração validada (`json`). */
    public record Analise(Integer empresaId, String url, String pasta, String json, Instant expiraEm) {}

    /** Identificador devolvido ao portal e até quando ele vale. */
    public record Emitida(String analiseId, Instant expiraEm) {}

    private static final String VERSAO = "v1";
    private static final String ALGORITMO = "HmacSHA256";
    /** Separa a chave desta assinatura de outros usos do mesmo segredo. */
    private static final byte[] FINALIDADE = "quimia-fds-analise-v1".getBytes(StandardCharsets.US_ASCII);
    private static final int SEGREDO_MINIMO_BYTES = 32;
    private static final int PARTES = 3;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Base64.Encoder CODIFICADOR = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODIFICADOR = Base64.getUrlDecoder();

    private final byte[] chave;
    private final Duration validade;
    private final Clock relogio;

    @Autowired
    public AnalisesDeFds(
            @Value("${quimia.fds.analise-segredo:}") String segredo,
            @Value("${quimia.fds.analise-validade-minutos:30}") long validadeMinutos) {
        this(segredo, Duration.ofMinutes(validadeMinutos), Clock.systemUTC());
    }

    AnalisesDeFds(String segredo, Duration validade, Clock relogio) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < SEGREDO_MINIMO_BYTES) {
            throw new IllegalStateException(
                    "QUIMIA_FDS_ANALISE_SEGREDO (or APP_AUTH_HMAC_SECRET) must contain at least 32 bytes");
        }
        if (validade.isZero() || validade.isNegative()) {
            throw new IllegalStateException("QUIMIA_FDS_ANALISE_VALIDADE_MINUTOS must be positive");
        }
        this.chave = hmac(bytes, FINALIDADE);
        this.validade = validade;
        this.relogio = relogio;
    }

    /** Identificador assinado de uma análise nova, válido pelo prazo configurado. */
    public Emitida emitir(Integer empresaId, String url, String pasta, String json) {
        Instant expiraEm = relogio.instant().plus(validade);
        ObjectNode payload = JSON.createObjectNode();
        payload.put("e", empresaId);
        payload.put("u", url);
        payload.put("p", pasta);
        payload.put("j", json);
        payload.put("x", expiraEm.getEpochSecond());
        String codificado =
                CODIFICADOR.encodeToString(JSON.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
        String corpo = VERSAO + "." + codificado;
        return new Emitida(corpo + "." + CODIFICADOR.encodeToString(assinar(corpo)), expiraEm);
    }

    /** Análise da empresa ainda válida; lança {@link AnaliseDeFdsException} se for inválida ou tiver expirado. */
    public Analise abrir(String analiseId, Integer empresaId) {
        Analise analise = abrirMesmoExpirada(analiseId, empresaId);
        if (!analise.expiraEm().isAfter(relogio.instant())) {
            throw new AnaliseDeFdsException(AnaliseDeFdsException.Motivo.EXPIRADA);
        }
        return analise;
    }

    /** Para descartar o PDF: uma análise vencida continua sendo da empresa, então a assinatura basta. */
    public Analise abrirMesmoExpirada(String analiseId, Integer empresaId) {
        String[] partes = analiseId == null ? new String[0] : analiseId.split("\\.", -1);
        if (partes.length != PARTES || !VERSAO.equals(partes[0])) {
            throw invalida();
        }
        byte[] assinatura;
        byte[] payload;
        try {
            assinatura = DECODIFICADOR.decode(partes[2]);
            payload = DECODIFICADOR.decode(partes[1]);
        } catch (IllegalArgumentException base64Invalido) {
            throw invalida();
        }
        if (!MessageDigest.isEqual(assinatura, assinar(partes[0] + "." + partes[1]))) {
            throw invalida();
        }
        Analise analise = ler(payload);
        if (!analise.empresaId().equals(empresaId)) {
            throw invalida();
        }
        return analise;
    }

    private static Analise ler(byte[] payload) {
        try {
            JsonNode no = JSON.readTree(new String(payload, StandardCharsets.UTF_8));
            boolean completo = no.path("e").isInt()
                    && no.path("u").isString()
                    && no.path("p").isString()
                    && no.path("j").isString()
                    && no.path("x").isIntegralNumber();
            if (!completo) {
                throw invalida();
            }
            return new Analise(
                    no.path("e").intValue(),
                    no.path("u").asString(),
                    no.path("p").asString(),
                    no.path("j").asString(),
                    Instant.ofEpochSecond(no.path("x").longValue()));
        } catch (JacksonException ilegivel) {
            throw invalida();
        }
    }

    private byte[] assinar(String texto) {
        return hmac(chave, texto.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] hmac(byte[] chave, byte[] dados) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(chave, ALGORITMO));
            return mac.doFinal(dados);
        } catch (GeneralSecurityException falha) {
            throw new IllegalStateException("Could not compute HMAC", falha);
        }
    }

    private static AnaliseDeFdsException invalida() {
        return new AnaliseDeFdsException(AnaliseDeFdsException.Motivo.INVALIDA);
    }
}
