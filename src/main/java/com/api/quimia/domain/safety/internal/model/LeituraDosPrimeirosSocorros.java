package com.api.quimia.domain.safety.internal.model;

import com.api.quimia.domain.safety.dto.PrimeirosSocorrosView.PrimeiroSocorroView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê a seção 4 do JSON do extrator (`secoes.04`). O JSON veio de fora e pode ser antigo ou malformado: o que não
 * segue o contrato é descartado item a item, e nada aqui lança exceção.
 */
public final class LeituraDosPrimeirosSocorros {
    /** Teto por texto e por lista: protege a tela e a resposta de um documento anômalo. */
    static final int TEXTO_MAXIMO = 2000;
    static final int ITENS_MAXIMOS = 20;

    private static final Logger log = LoggerFactory.getLogger(LeituraDosPrimeirosSocorros.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Ordem de exibição; o nome é o código do contrato. */
    private enum Rota {
        INALACAO,
        PELE,
        OLHOS,
        INGESTAO;

        String codigo() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Rota doCodigo(String codigo) {
            for (Rota rota : values()) {
                if (rota.codigo().equals(codigo)) {
                    return rota;
                }
            }
            return null;
        }
    }

    public record Secao(
            boolean encontrada,
            List<PrimeiroSocorroView> porExposicao,
            List<String> orientacoesGerais,
            List<String> notasAoMedico) {
        public static final Secao AUSENTE = new Secao(false, List.of(), List.of(), List.of());
    }

    private LeituraDosPrimeirosSocorros() {}

    public static Secao ler(String rawJson) {
        if (rawJson == null) {
            return Secao.AUSENTE;
        }
        JsonNode raiz;
        try {
            raiz = JSON.readTree(rawJson);
        } catch (JacksonException ilegivel) {
            log.warn("raw_json da FDS ilegível: {}", ilegivel.getOriginalMessage());
            return Secao.AUSENTE;
        }
        JsonNode secao = raiz.path("secoes").path("04");
        JsonNode encontrada = secao.path("encontrada");
        if (!encontrada.isBoolean() || !encontrada.asBoolean()) {
            return Secao.AUSENTE;
        }
        return new Secao(
                true,
                porExposicao(secao.path("primeiros_socorros")),
                textos(secao.path("orientacoes_gerais")),
                textos(secao.path("notas_ao_medico")));
    }

    /** Um registro por rota (o primeiro vale), na ordem das rotas; item sem nenhuma informação é descartado. */
    private static List<PrimeiroSocorroView> porExposicao(JsonNode itens) {
        Map<Rota, PrimeiroSocorroView> porRota = new EnumMap<>(Rota.class);
        if (!itens.isArray()) {
            return List.of();
        }
        for (JsonNode item : itens) {
            Rota rota = Rota.doCodigo(texto(item.path("rota_exposicao")));
            if (rota == null || porRota.containsKey(rota)) {
                continue;
            }
            String descricao = texto(item.path("descricao"));
            String sintomas = texto(item.path("sintomas"));
            String tratamento = texto(item.path("tratamento_especial"));
            boolean atencao = item.path("atencao_medica_imediata").isBoolean()
                    && item.path("atencao_medica_imediata").asBoolean();
            if (descricao != null || sintomas != null || tratamento != null || atencao) {
                porRota.put(rota, new PrimeiroSocorroView(rota.codigo(), descricao, sintomas, tratamento, atencao));
            }
        }
        return List.copyOf(porRota.values());
    }

    /** Lista de `{descricao}` (ou de textos soltos), sem vazios, até {@value #ITENS_MAXIMOS} itens. */
    private static List<String> textos(JsonNode itens) {
        List<String> textos = new ArrayList<>();
        if (!itens.isArray()) {
            return List.of();
        }
        for (JsonNode item : itens) {
            String texto = texto(item.isObject() ? item.path("descricao") : item);
            if (texto != null && textos.size() < ITENS_MAXIMOS) {
                textos.add(texto);
            }
        }
        return List.copyOf(textos);
    }

    /** Texto aparado, ou `null` se não for texto ou estiver vazio; cortado em {@value #TEXTO_MAXIMO} caracteres. */
    private static String texto(JsonNode valor) {
        if (!valor.isString()) {
            return null;
        }
        String texto = valor.asString().strip();
        if (texto.isEmpty()) {
            return null;
        }
        if (texto.length() <= TEXTO_MAXIMO) {
            return texto;
        }
        // Não corta um caractere fora do plano básico (emoji, por exemplo) ao meio.
        int fim = Character.isHighSurrogate(texto.charAt(TEXTO_MAXIMO - 1)) ? TEXTO_MAXIMO - 1 : TEXTO_MAXIMO;
        return texto.substring(0, fim);
    }
}
