package com.api.quimia.domain.catalog.internal.fds;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Aplica à extração o que a empresa informou na revisão das pendências. Só a unidade de concentração de um composto
 * que veio sem ela pode ser preenchida; a pendência que esse valor resolve sai da lista, e as demais seguem no
 * `raw_json` como registro do que a empresa aceitou.
 */
public final class CorrecaoDaExtracao {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CAMPO_UNIDADE = "unidade_concentracao";
    private static final String SECAO_COMPOSICAO = "03";

    private CorrecaoDaExtracao() {}

    /** JSON corrigido e de novo conferido contra o que o banco aceita; lança {@link AnaliseDeFdsException}. */
    public static String aplicar(String extracao, List<CorrecaoDeComposto> correcoes) {
        if (correcoes.isEmpty()) {
            return extracao;
        }
        ObjectNode raiz = lerRaiz(extracao);
        JsonNode compostos = raiz.path("secoes").path(SECAO_COMPOSICAO).path("compostos");
        if (!compostos.isArray()) {
            throw recusada();
        }
        Set<Integer> corrigidos = new HashSet<>();
        for (CorrecaoDeComposto correcao : correcoes) {
            if (!corrigidos.add(correcao.indice())) {
                throw recusada();
            }
            ObjectNode composto = compostoSemUnidade(compostos, correcao.indice());
            composto.put(CAMPO_UNIDADE, unidadeValida(correcao.unidadeConcentracao()));
        }
        removerPendenciasResolvidas(raiz, corrigidos);
        String corrigido = JSON.writeValueAsString(raiz);
        ValidacaoDaExtracao.validar(corrigido);
        return corrigido;
    }

    private static ObjectNode lerRaiz(String extracao) {
        try {
            JsonNode raiz = JSON.readTree(extracao);
            if (raiz instanceof ObjectNode objeto) {
                return objeto;
            }
        } catch (JacksonException ilegivel) {
            // Cai na recusa abaixo: a extração já foi validada ao ser guardada, então só chega aqui adulterada.
        }
        throw recusada();
    }

    private static ObjectNode compostoSemUnidade(JsonNode compostos, int indice) {
        if (indice < 0 || indice >= compostos.size() || !(compostos.get(indice) instanceof ObjectNode composto)) {
            throw recusada();
        }
        JsonNode atual = composto.path(CAMPO_UNIDADE);
        if (!(atual.isMissingNode() || atual.isNull())) {
            throw recusada();
        }
        return composto;
    }

    private static String unidadeValida(String unidade) {
        String limpa = unidade == null ? "" : unidade.strip();
        boolean valida = !limpa.isEmpty()
                && limpa.length() <= CorrecaoDeComposto.UNIDADE_MAXIMA
                && limpa.chars().noneMatch(Character::isISOControl);
        if (!valida) {
            throw recusada();
        }
        return limpa;
    }

    private static void removerPendenciasResolvidas(ObjectNode raiz, Set<Integer> corrigidos) {
        JsonNode pendencias = raiz.path("pendencias");
        if (!pendencias.isArray()) {
            return;
        }
        ArrayNode restantes = JSON.createArrayNode();
        for (JsonNode pendencia : pendencias) {
            if (!resolvida(pendencia, corrigidos)) {
                restantes.add(pendencia);
            }
        }
        raiz.set("pendencias", restantes);
        if (restantes.isEmpty()) {
            raiz.put("status", "processado");
            raiz.putNull("mensagem");
        }
    }

    private static boolean resolvida(JsonNode pendencia, Set<Integer> corrigidos) {
        JsonNode indice = pendencia.path("composto").path("indice");
        return SECAO_COMPOSICAO.equals(pendencia.path("secao").asString(""))
                && CAMPO_UNIDADE.equals(pendencia.path("campo").asString(""))
                && indice.isInt()
                && corrigidos.contains(indice.intValue());
    }

    private static AnaliseDeFdsException recusada() {
        return new AnaliseDeFdsException(AnaliseDeFdsException.Motivo.CORRECAO_INVALIDA);
    }
}
