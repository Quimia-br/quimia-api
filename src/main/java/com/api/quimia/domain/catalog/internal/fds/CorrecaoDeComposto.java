package com.api.quimia.domain.catalog.internal.fds;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Valor que a empresa informou para um composto da FDS que o extrator deixou sem unidade de concentração. */
public record CorrecaoDeComposto(int indice, String unidadeConcentracao) {
    /** `fds_composto.unidade_concentracao` é VARCHAR(20). */
    static final int UNIDADE_MAXIMA = 20;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * Lê o campo `correcoesFds` do formulário: lista JSON de `{"indice": 0, "unidadeConcentracao": "%"}`. Vazio ou
     * ausente vale como nenhuma correção; qualquer outra forma é recusada.
     */
    public static List<CorrecaoDeComposto> ler(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        JsonNode raiz;
        try {
            raiz = JSON.readTree(texto);
        } catch (JacksonException ilegivel) {
            throw recusada();
        }
        if (raiz == null || !raiz.isArray()) {
            throw recusada();
        }
        List<CorrecaoDeComposto> correcoes = new ArrayList<>();
        for (JsonNode item : raiz) {
            JsonNode indice = item.path("indice");
            JsonNode unidade = item.path("unidadeConcentracao");
            if (!item.isObject() || !indice.isInt() || !unidade.isString()) {
                throw recusada();
            }
            correcoes.add(new CorrecaoDeComposto(indice.intValue(), unidade.asString()));
        }
        return correcoes;
    }

    private static AnaliseDeFdsException recusada() {
        return new AnaliseDeFdsException(AnaliseDeFdsException.Motivo.CORRECAO_INVALIDA);
    }
}
