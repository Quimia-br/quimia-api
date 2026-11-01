package com.api.quimia.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.safety.dto.AvaliacaoItensView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import com.api.quimia.domain.safety.dto.ItemParaAvaliar;
import com.api.quimia.domain.safety.internal.model.ComposicaoDoProduto;
import com.api.quimia.domain.safety.internal.model.IncompatibilidadeDaFds;
import com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade;
import com.api.quimia.domain.safety.internal.model.RegraDeIncompatibilidade;
import com.api.quimia.domain.safety.internal.model.Severidade;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Mistura de itens, cada um produto ou substância solta. O produto 10 tem a substância 1; o 20, a substância 2 e um
 * composto não identificado; o 30 não tem composição. A substância solta 7 é da classe 9; a 8 não tem classe.
 */
class MotorDeCompatibilidadeItensTest {
    private static final int A = 10;
    private static final int B = 20;
    private static final int SEM_COMPOSICAO = 30;
    private static final int SUBSTANCIA = 7;
    private static final int OUTRA_SUBSTANCIA = 8;
    private static final int CLASSE = 9;
    private static final Map<Integer, ComposicaoDoProduto> COMPOSICOES = Map.of(
            A, new ComposicaoDoProduto(Set.of(1), Set.of(), false),
            B, new ComposicaoDoProduto(Set.of(2), Set.of(), true));
    private static final Map<Integer, ComposicaoDoProduto> AVULSAS = Map.of(
            SUBSTANCIA, new ComposicaoDoProduto(Set.of(SUBSTANCIA), Set.of(CLASSE), false),
            OUTRA_SUBSTANCIA, new ComposicaoDoProduto(Set.of(OUTRA_SUBSTANCIA), Set.of(), false));

    private static MotorDeCompatibilidade motor(
            List<RegraDeIncompatibilidade> regras, IncompatibilidadeDaFds... declaradas) {
        return new MotorDeCompatibilidade(COMPOSICOES, AVULSAS, regras, List.of(declaradas));
    }

    private static RegraDeIncompatibilidade regra(int id, Integer substanciaA, Integer classeA, Integer substanciaB,
            Integer classeB, Severidade severidade) {
        return new RegraDeIncompatibilidade(id, substanciaA, classeA, substanciaB, classeB, severidade, "regra " + id);
    }

    private static ItemParaAvaliar produto(int id) {
        return ItemParaAvaliar.produto(id);
    }

    private static ItemParaAvaliar substancia(int id) {
        return ItemParaAvaliar.substancia(id);
    }

    @Test
    void produtoComSubstanciaCasaARegraNasDuasOrdens() {
        MotorDeCompatibilidade motor = motor(List.of(regra(1, 1, null, SUBSTANCIA, null, Severidade.CRITICA)));
        AvaliacaoItensView esperado = new AvaliacaoItensView("incompativel", "critica", "regra 1", 1);

        assertThat(motor.avaliar(produto(A), substancia(SUBSTANCIA))).isEqualTo(esperado);
        assertThat(motor.avaliar(substancia(SUBSTANCIA), produto(A))).isEqualTo(esperado);
    }

    @Test
    void duasSubstanciasCasamARegra() {
        MotorDeCompatibilidade motor =
                motor(List.of(regra(2, SUBSTANCIA, null, OUTRA_SUBSTANCIA, null, Severidade.ALTA)));

        assertThat(motor.avaliar(substancia(OUTRA_SUBSTANCIA), substancia(SUBSTANCIA)))
                .isEqualTo(new AvaliacaoItensView("incompativel", "alta", "regra 2", 2));
    }

    @Test
    void substanciaSoltaCasaPelaClasse() {
        MotorDeCompatibilidade motor = motor(List.of(regra(3, 1, null, null, CLASSE, Severidade.MEDIA)));

        assertThat(motor.avaliar(produto(A), substancia(SUBSTANCIA)).resultado()).isEqualTo("incompativel");
        assertThat(motor.avaliar(produto(A), substancia(OUTRA_SUBSTANCIA)).resultado()).isEqualTo("compativel");
    }

    @Test
    void oQueAFdsDoProdutoDeclaraAtingeASubstanciaSolta() {
        MotorDeCompatibilidade motor = motor(
                List.of(), new IncompatibilidadeDaFds(A, SUBSTANCIA, null, Severidade.ALTA, "declarada por 10"));

        assertThat(motor.avaliar(produto(A), substancia(SUBSTANCIA)))
                .isEqualTo(new AvaliacaoItensView("incompativel", "alta", "declarada por 10", null));
        assertThat(motor.avaliar(produto(B), substancia(SUBSTANCIA)).resultado()).isEqualTo("nao_avaliado");
    }

    @Test
    void declaracaoDeProdutoDeForaDoParNaoAtinge() {
        MotorDeCompatibilidade motor = motor(
                List.of(), new IncompatibilidadeDaFds(B, OUTRA_SUBSTANCIA, null, Severidade.CRITICA, "de fora"));

        assertThat(motor.avaliar(produto(A), substancia(OUTRA_SUBSTANCIA)).resultado()).isEqualTo("compativel");
    }

    @Test
    void substanciaDesconhecidaOuProdutoSemComposicaoNaoSaoAvaliados() {
        MotorDeCompatibilidade motor = motor(List.of());
        AvaliacaoItensView semComposicao = new AvaliacaoItensView(
                "nao_avaliado", null, MotorDeCompatibilidade.SEM_COMPOSICAO, null);

        assertThat(motor.avaliar(produto(A), substancia(99))).isEqualTo(semComposicao);
        assertThat(motor.avaliar(produto(SEM_COMPOSICAO), substancia(SUBSTANCIA))).isEqualTo(semComposicao);
    }

    @Test
    void compostoNaoIdentificadoDoProdutoNaoEscondeARegraEMantemONaoAvaliadoSemRegra() {
        assertThat(motor(List.of(regra(4, 2, null, SUBSTANCIA, null, Severidade.BAIXA)))
                        .avaliar(produto(B), substancia(SUBSTANCIA)))
                .isEqualTo(new AvaliacaoItensView("incompativel", "baixa", "regra 4", 4));
        assertThat(motor(List.of()).avaliar(produto(B), substancia(SUBSTANCIA)))
                .isEqualTo(new AvaliacaoItensView(
                        "nao_avaliado", null, MotorDeCompatibilidade.COMPOSICAO_INCOMPLETA, null));
    }

    @Test
    void semRegraNemDeclaracaoEhCompativel() {
        assertThat(motor(List.of()).avaliar(substancia(SUBSTANCIA), substancia(OUTRA_SUBSTANCIA)))
                .isEqualTo(new AvaliacaoItensView("compativel", null, null, null));
    }

    @Test
    void doisProdutosDaoOMesmoResultadoQueOPar() {
        MotorDeCompatibilidade motor = motor(List.of(regra(5, 1, null, 2, null, Severidade.CRITICA)));
        AvaliacaoParView par = motor.par(A, B);
        AvaliacaoItensView itens = motor.avaliar(produto(B), produto(A));

        assertThat(itens)
                .isEqualTo(new AvaliacaoItensView(
                        par.resultado(), par.severidade(), par.descricaoRisco(), par.regraId()));
    }

    @Test
    void itensIguaisOuNulosNaoFormamPar() {
        MotorDeCompatibilidade motor = motor(List.of());

        assertThatThrownBy(() -> motor.avaliar(produto(A), produto(A)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("itens_iguais");
        assertThatThrownBy(() -> motor.avaliar(substancia(SUBSTANCIA), substancia(SUBSTANCIA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("itens_iguais");
        assertThatThrownBy(() -> motor.avaliar(null, produto(A)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("itens_iguais");
    }

    @Test
    void umLadoPrecisaDeProdutoOuSubstanciaMasNuncaOsDois() {
        assertThatThrownBy(() -> new ItemParaAvaliar(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("item_invalido");
        assertThatThrownBy(() -> new ItemParaAvaliar(1, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("item_invalido");
    }
}
