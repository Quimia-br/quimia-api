package com.api.quimia.domain.catalog.internal.fds;

import java.math.BigDecimal;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Confere a resposta do extrator antes de gravá-la em `fds.raw_json`. O trigger do banco
 * (`fn_processar_fds_raw_json`) não trata exceção: um campo fora destas regras abortaria o cadastro inteiro do
 * produto no banco. Resposta fora do contrato é falha do extrator.
 */
public final class ValidacaoDaExtracao {
    private static final Logger log = LoggerFactory.getLogger(ValidacaoDaExtracao.class);
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Set<String> STATUS = Set.of("processado", "processado_com_pendencias");
    /** CHECK de `fds_incompatibilidade.severidade`. */
    private static final Set<String> SEVERIDADES = Set.of("baixa", "media", "alta", "critica");
    /** CHECK de `fds_descarte.tipo_residuo`. */
    private static final Set<String> TIPOS_DE_RESIDUO = Set.of("quimico", "biologico", "comum", "reciclavel", "outro");
    /** `fds_composto.concentracao_*` é NUMERIC(5,2). */
    private static final BigDecimal CONCENTRACAO_MAXIMA = new BigDecimal("999.99");
    /** `sinonimo_pendente.termo_bruto` e `fds_incompatibilidade.substancia_reagente` são VARCHAR(255). */
    private static final int TEXTO_MAXIMO = 255;
    /** `sinonimo_pendente.cas_bruto` é VARCHAR(20). */
    private static final int CAS_MAXIMO = 20;
    /** O `jsonb` do PostgreSQL recusa o caractere NUL em qualquer texto. */
    private static final char NUL = '\0';

    private ValidacaoDaExtracao() {}

    /** Lança {@link ExtratorFdsException} com motivo FALHOU quando a resposta não pode ser gravada. */
    public static void validar(String resposta) {
        JsonNode raiz;
        try {
            raiz = JSON.readTree(resposta == null ? "" : resposta);
        } catch (JacksonException ilegivel) {
            throw invalida("JSON ilegível");
        }
        if (raiz == null || !raiz.isObject()) {
            throw invalida("raiz não é objeto");
        }
        if (contemNul(raiz)) {
            throw invalida("texto com caractere NUL");
        }
        if (!raiz.path("status").isString() || !STATUS.contains(raiz.path("status").asString())) {
            throw invalida("status fora da lista");
        }
        JsonNode secoes = raiz.path("secoes");
        if (!secoes.isObject()) {
            throw invalida("secoes ausente");
        }
        validarCompostos(secoes.path("03").path("compostos"));
        validarIncompatibilidades(secoes.path("10").path("incompatibilidades"));
        validarDescartes(secoes.path("13").path("descartes"));
    }

    private static void validarCompostos(JsonNode compostos) {
        if (!compostos.isArray()) {
            throw invalida("secoes.03.compostos não é lista");
        }
        for (JsonNode composto : compostos) {
            if (!composto.isObject()) {
                throw invalida("composto não é objeto");
            }
            JsonNode nome = composto.path("nome");
            if (!nome.isString() || nome.asString().isBlank() || nome.asString().length() > TEXTO_MAXIMO) {
                throw invalida("composto sem nome válido");
            }
            if (!textoOpcional(composto.path("cas_numero"), CAS_MAXIMO)) {
                throw invalida("cas_numero inválido");
            }
            BigDecimal minimo = concentracao(composto.path("concentracao_min"));
            BigDecimal maximo = concentracao(composto.path("concentracao_max"));
            if (minimo != null && maximo != null && minimo.compareTo(maximo) > 0) {
                throw invalida("concentracao_min maior que concentracao_max");
            }
        }
    }

    private static void validarIncompatibilidades(JsonNode incompatibilidades) {
        for (JsonNode incompatibilidade : listaOpcional(incompatibilidades, "secoes.10.incompatibilidades")) {
            if (!textoOpcional(incompatibilidade.path("substancia_reagente"), TEXTO_MAXIMO)) {
                throw invalida("substancia_reagente inválida");
            }
            if (!valorOpcionalDaLista(incompatibilidade.path("severidade"), SEVERIDADES)) {
                throw invalida("severidade fora da lista");
            }
        }
    }

    /** O extrator ainda não devolve a seção 13, mas o trigger a processa sempre que ela vem. */
    private static void validarDescartes(JsonNode descartes) {
        for (JsonNode descarte : listaOpcional(descartes, "secoes.13.descartes")) {
            JsonNode instrucao = descarte.path("instrucao_descarte");
            if (!(instrucao.isMissingNode() || instrucao.isNull() || instrucao.isString())) {
                throw invalida("instrucao_descarte não é texto");
            }
            if (!valorOpcionalDaLista(descarte.path("tipo_residuo"), TIPOS_DE_RESIDUO)) {
                throw invalida("tipo_residuo fora da lista");
            }
        }
    }

    /** Ausente ou nula vale como lista vazia; presente, tem de ser lista de objetos. */
    private static Iterable<JsonNode> listaOpcional(JsonNode lista, String caminho) {
        if (lista.isMissingNode() || lista.isNull()) {
            return Set.of();
        }
        if (!lista.isArray()) {
            throw invalida(caminho + " não é lista");
        }
        for (JsonNode item : lista) {
            if (!item.isObject()) {
                throw invalida(caminho + " tem item que não é objeto");
            }
        }
        return lista;
    }

    private static boolean textoOpcional(JsonNode valor, int tamanhoMaximo) {
        return valor.isMissingNode() || valor.isNull() || (valor.isString() && valor.asString().length() <= tamanhoMaximo);
    }

    private static boolean valorOpcionalDaLista(JsonNode valor, Set<String> permitidos) {
        return valor.isMissingNode() || valor.isNull() || (valor.isString() && permitidos.contains(valor.asString()));
    }

    /** `null` quando ausente; lança se não for número finito entre 0 e 999,99. */
    private static BigDecimal concentracao(JsonNode valor) {
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        if (!valor.isNumber() || ((valor.isDouble() || valor.isFloat()) && !Double.isFinite(valor.doubleValue()))) {
            throw invalida("concentração não é número finito");
        }
        BigDecimal numero = valor.decimalValue();
        if (numero.signum() < 0 || numero.compareTo(CONCENTRACAO_MAXIMA) > 0) {
            throw invalida("concentração fora de 0 a 999,99");
        }
        return numero;
    }

    private static boolean contemNul(JsonNode no) {
        if (no.isString()) {
            return no.asString().indexOf(NUL) >= 0;
        }
        if (no.isObject()) {
            for (var campo : no.properties()) {
                if (campo.getKey().indexOf(NUL) >= 0 || contemNul(campo.getValue())) {
                    return true;
                }
            }
        }
        if (no.isArray()) {
            for (JsonNode item : no) {
                if (contemNul(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ExtratorFdsException invalida(String motivo) {
        log.error("resposta do extrator de FDS fora do contrato: {}", motivo);
        return new ExtratorFdsException(ExtratorFdsException.Motivo.FALHOU);
    }
}
