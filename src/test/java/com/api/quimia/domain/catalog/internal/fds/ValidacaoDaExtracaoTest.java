package com.api.quimia.domain.catalog.internal.fds;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.catalog.FakeExtratorFds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** O que o trigger `fn_processar_fds_raw_json` aceita sem abortar o cadastro. */
class ValidacaoDaExtracaoTest {
    private static final String EXEMPLO = FakeExtratorFds.RESPOSTA_DE_EXEMPLO;

    private static void invalida(String json) {
        assertThatThrownBy(() -> ValidacaoDaExtracao.validar(json))
                .isInstanceOfSatisfying(ExtratorFdsException.class,
                        erro -> org.assertj.core.api.Assertions.assertThat(erro.motivo())
                                .isEqualTo(ExtratorFdsException.Motivo.FALHOU));
    }

    @Test
    void respostaDeExemploDoExtratorEhAceita() {
        assertThatCode(() -> ValidacaoDaExtracao.validar(EXEMPLO)).doesNotThrowAnyException();
    }

    @Test
    void comPendenciasSemComposicaoESemSecao10TambemEhAceita() {
        assertThatCode(() -> ValidacaoDaExtracao.validar(
                        "{\"status\":\"processado_com_pendencias\",\"secoes\":{\"03\":{\"compostos\":[]}}}"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "nao e json", "[]", "{}", "{\"status\":\"ok\",\"secoes\":{}}",
        "{\"status\":\"processado\"}", "{\"status\":\"processado\",\"secoes\":{\"03\":{}}}",
        "{\"status\":\"processado\",\"secoes\":{\"03\":{\"compostos\":{}}}}"})
    void formaGeralErradaEhRecusada(String json) {
        invalida(json);
    }

    @Test
    void compostoForaDasColunasDoBancoEhRecusado() {
        invalida(EXEMPLO.replace("\"Etanol\"", "\"\""));
        invalida(EXEMPLO.replace("\"Etanol\"", "\"" + "a".repeat(256) + "\""));
        invalida(EXEMPLO.replace("\"64-17-5\"", "\"" + "1".repeat(21) + "\""));
        invalida(EXEMPLO.replace("\"concentracao_min\": 5", "\"concentracao_min\": -1"));
        invalida(EXEMPLO.replace("\"concentracao_max\": 10", "\"concentracao_max\": 1000"));
        invalida(EXEMPLO.replace("\"concentracao_max\": 10", "\"concentracao_max\": \"10\""));
        invalida(EXEMPLO.replace("\"concentracao_min\": 5", "\"concentracao_min\": 50"));
    }

    @Test
    void incompatibilidadeForaDasRegrasDoBancoEhRecusada() {
        invalida(EXEMPLO.replace("\"alta\"", "\"extrema\""));
        invalida(EXEMPLO.replace("\"Agentes oxidantes fortes\"", "\"" + "b".repeat(256) + "\""));
        invalida(EXEMPLO.replace("\"incompatibilidades\": [", "\"incompatibilidades\": {\"x\": ["));
    }

    @Test
    void secao13ForaDasRegrasDoBancoEhRecusadaEAusenteEhAceita() {
        String comDescarte = EXEMPLO.replace("\"secoes\": {", "\"secoes\": {\"13\": {\"descartes\": DESCARTES},");
        assertThatCode(() -> ValidacaoDaExtracao.validar(comDescarte.replace("DESCARTES",
                        "[{\"instrucao_descarte\": \"Coleta seletiva\", \"tipo_residuo\": \"quimico\"}]")))
                .doesNotThrowAnyException();
        invalida(comDescarte.replace("DESCARTES", "{\"x\": 1}"));
        invalida(comDescarte.replace("DESCARTES", "[{\"tipo_residuo\": \"radioativo\"}]"));
        invalida(comDescarte.replace("DESCARTES", "[{\"instrucao_descarte\": 3}]"));
        invalida(comDescarte.replace("DESCARTES", "[\"jogar no lixo\"]"));
    }

    @Test
    void itemQueNaoEhObjetoNumeroInfinitoNulETextoSobrandoSaoRecusados() {
        invalida(EXEMPLO.replace("\"incompatibilidades\": [", "\"incompatibilidades\": [\"oxidantes\", "));
        invalida(EXEMPLO.replace("\"compostos\": [", "\"compostos\": [42, "));
        invalida(EXEMPLO.replace("\"concentracao_max\": 10", "\"concentracao_max\": 1e400"));
        invalida(EXEMPLO.replace("Levar para local ventilado.\",", "Levar para local\\u0000 ventilado.\","));
        invalida(EXEMPLO + " {}");
    }

    @Test
    void camposOpcionaisNulosSaoAceitos() {
        assertThatCode(() -> ValidacaoDaExtracao.validar(EXEMPLO
                        .replace("\"64-17-5\"", "null")
                        .replace("\"concentracao_min\": 5", "\"concentracao_min\": null")
                        .replace("\"alta\"", "null")))
                .doesNotThrowAnyException();
    }

    /** A function do banco aborta o cadastro com `cannot extract elements from a scalar` nestes casos. */
    @Test
    void primeirosSocorrosForaDoQueAFunctionAceitaSaoRecusados() {
        invalida(EXEMPLO.replace("\"primeiros_socorros\": [", "\"primeiros_socorros\": \"lavar\", \"x\": ["));
        invalida(EXEMPLO.replace("\"primeiros_socorros\": [", "\"primeiros_socorros\": null, \"x\": ["));
        invalida(EXEMPLO.replace("\"primeiros_socorros\": [", "\"primeiros_socorros\": {\"a\": 1}, \"x\": ["));
        invalida(EXEMPLO.replace("\"primeiros_socorros\": [", "\"primeiros_socorros\": [\"lavar\"], \"x\": ["));
        invalida(EXEMPLO.replace("\"atencao_medica_imediata\": true", "\"atencao_medica_imediata\": \"sim\""));
        invalida(EXEMPLO.replace("\"descricao\": \"Levar para local ventilado.\"", "\"descricao\": 7"));
        invalida(EXEMPLO.replace("\"rota_exposicao\": \"inalacao\"", "\"rota_exposicao\": {\"a\": 1}"));
    }

    @Test
    void primeirosSocorrosAusentesOuComRotaDesconhecidaSaoAceitos() {
        assertThatCode(() -> ValidacaoDaExtracao.validar(
                        "{\"status\":\"processado\",\"secoes\":{\"03\":{\"compostos\":[]},\"04\":{\"encontrada\":false}}}"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ValidacaoDaExtracao.validar(
                        EXEMPLO.replace("\"rota_exposicao\": \"inalacao\"", "\"rota_exposicao\": \"outra\"")
                                .replace("\"atencao_medica_imediata\": true", "\"atencao_medica_imediata\": null")))
                .doesNotThrowAnyException();
    }

    @Test
    void secao13NulaAbortariaOCadastroNoBanco() {
        invalida(EXEMPLO.replace("\"secoes\": {", "\"secoes\": {\"13\": {\"descartes\": null},"));
    }
}
