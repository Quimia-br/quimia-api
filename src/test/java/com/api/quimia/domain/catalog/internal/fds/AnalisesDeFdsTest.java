package com.api.quimia.domain.catalog.internal.fds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AnalisesDeFdsTest {
    private static final String SEGREDO = "segredo-de-teste-com-pelo-menos-32-bytes";
    private static final Instant AGORA = Instant.parse("2026-10-26T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofMinutes(30);
    private static final String URL = "https://fake.docs/fds/7/abc/ficha.pdf";
    private static final String PASTA = "fds/7";
    private static final String JSON = "{\"status\":\"processado\",\"texto\":\"ação – ü\"}";

    private static AnalisesDeFds analises(String segredo, Instant agora) {
        return new AnalisesDeFds(segredo, VALIDADE, Clock.fixed(agora, ZoneOffset.UTC));
    }

    private static void recusa(Runnable acao, AnaliseDeFdsException.Motivo motivo) {
        assertThatThrownBy(acao::run)
                .isInstanceOfSatisfying(
                        AnaliseDeFdsException.class, erro -> assertThat(erro.motivo()).isEqualTo(motivo));
    }

    @Test
    void ciclaOPayloadSemPerdaEComValidadeFixa() {
        AnalisesDeFds analises = analises(SEGREDO, AGORA);

        AnalisesDeFds.Emitida emitida = analises.emitir(7, URL, PASTA, JSON);
        AnalisesDeFds.Analise aberta = analises.abrir(emitida.analiseId(), 7);

        assertThat(emitida.analiseId()).startsWith("v1.").matches("[A-Za-z0-9_.-]+");
        assertThat(emitida.expiraEm()).isEqualTo(AGORA.plus(VALIDADE));
        assertThat(aberta).isEqualTo(new AnalisesDeFds.Analise(7, URL, PASTA, JSON, AGORA.plus(VALIDADE)));
    }

    @Test
    void outraEmpresaNaoAbreAAnalise() {
        AnalisesDeFds analises = analises(SEGREDO, AGORA);
        String id = analises.emitir(7, URL, PASTA, JSON).analiseId();

        recusa(() -> analises.abrir(id, 8), AnaliseDeFdsException.Motivo.INVALIDA);
        recusa(() -> analises.abrirMesmoExpirada(id, 8), AnaliseDeFdsException.Motivo.INVALIDA);
    }

    @Test
    void payloadAlteradoOuAssinaturaDeOutroSegredoSaoRecusados() {
        AnalisesDeFds analises = analises(SEGREDO, AGORA);
        String id = analises.emitir(7, URL, PASTA, JSON).analiseId();
        String[] partes = id.split("\\.");
        String payloadAdulterado = new String(Base64.getUrlDecoder().decode(partes[1]), java.nio.charset.StandardCharsets.UTF_8)
                .replace(PASTA, "fds/8");
        String adulterado = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payloadAdulterado.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + partes[2];

        recusa(() -> analises.abrir(adulterado, 7), AnaliseDeFdsException.Motivo.INVALIDA);
        recusa(() -> analises(SEGREDO + "-outro", AGORA).abrir(id, 7), AnaliseDeFdsException.Motivo.INVALIDA);
    }

    @Test
    void formatosQueNaoSaoUmaAnaliseSaoRecusados() {
        AnalisesDeFds analises = analises(SEGREDO, AGORA);
        String id = analises.emitir(7, URL, PASTA, JSON).analiseId();

        for (String invalido : new String[] {null, "", "abc", "v1.a", "v1.a.b.c", "v2" + id.substring(2), "v1..", "v1.!!.@@"}) {
            recusa(() -> analises.abrir(invalido, 7), AnaliseDeFdsException.Motivo.INVALIDA);
        }
    }

    @Test
    void expiradaNaoAbreMasAindaDeixaDescartarOPdf() {
        String id = analises(SEGREDO, AGORA).emitir(7, URL, PASTA, JSON).analiseId();
        AnalisesDeFds depois = analises(SEGREDO, AGORA.plus(VALIDADE));

        recusa(() -> depois.abrir(id, 7), AnaliseDeFdsException.Motivo.EXPIRADA);
        assertThat(depois.abrirMesmoExpirada(id, 7).url()).isEqualTo(URL);
        assertThat(analises(SEGREDO, AGORA.plus(VALIDADE).minusSeconds(1)).abrir(id, 7).pasta()).isEqualTo(PASTA);
    }

    @Test
    void configuracaoFracaImpedeASubida() {
        assertThatThrownBy(() -> analises("curto", AGORA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> analises("", AGORA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AnalisesDeFds(SEGREDO, Duration.ZERO, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
