package com.api.quimia.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
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
 * Incompatibilidade declarada na própria FDS (seção 10), como em `fn_match_produtos`. O produto 10 tem a substância 1
 * e a classe 2; o 20, a substância 2 e a classe 1; o 30, a substância 3.
 */
class MotorDeCompatibilidadeDeclaradasTest {
    private static final int A = 10;
    private static final int B = 20;
    private static final int C = 30;
    private static final Map<Integer, ComposicaoDoProduto> COMPOSICOES = Map.of(
            A, new ComposicaoDoProduto(Set.of(1), Set.of(2), false),
            B, new ComposicaoDoProduto(Set.of(2), Set.of(1), false),
            C, new ComposicaoDoProduto(Set.of(3), Set.of(), false));

    private static MotorDeCompatibilidade motor(
            List<RegraDeIncompatibilidade> regras, IncompatibilidadeDaFds... declaradas) {
        return new MotorDeCompatibilidade(COMPOSICOES, regras, List.of(declaradas));
    }

    private static IncompatibilidadeDaFds declara(int produto, Integer substancia, Integer classe, Severidade severidade) {
        return new IncompatibilidadeDaFds(produto, substancia, classe, severidade, "declarada por " + produto);
    }

    private static RegraDeIncompatibilidade regra(int id, Severidade severidade) {
        return new RegraDeIncompatibilidade(id, 1, null, 2, null, severidade, "regra " + id);
    }

    @Test
    void oQueUmaFdsDeclaraSobreASubstanciaOuClasseDoOutroProdutoIncompatibiliza() {
        AvaliacaoParView porSubstancia = motor(List.of(), declara(A, 2, null, Severidade.ALTA)).par(A, B);
        AvaliacaoParView porClasse = motor(List.of(), declara(B, null, 2, Severidade.BAIXA)).par(A, B);

        assertThat(porSubstancia)
                .isEqualTo(new AvaliacaoParView(A, B, "incompativel", "alta", "declarada por 10", null));
        assertThat(porClasse).isEqualTo(new AvaliacaoParView(A, B, "incompativel", "baixa", "declarada por 20", null));
    }

    @Test
    void naoDependeDaOrdemDosArgumentos() {
        MotorDeCompatibilidade motor = motor(List.of(), declara(B, 1, null, Severidade.MEDIA));

        assertThat(motor.par(B, A)).isEqualTo(motor.par(A, B));
        assertThat(motor.par(A, B).resultado()).isEqualTo("incompativel");
    }

    @Test
    void oQueAFdsDeclaraSobreSiMesmaOuSobreProdutoDeForaDoParNaoConta() {
        // A declara a própria substância (1) e a própria classe (2); C declara a substância 2, de B, mas C não está no par.
        MotorDeCompatibilidade motor = motor(
                List.of(), declara(A, 1, null, Severidade.CRITICA), declara(A, null, 2, Severidade.CRITICA),
                declara(C, 2, null, Severidade.CRITICA));

        assertThat(motor.par(A, B)).isEqualTo(new AvaliacaoParView(A, B, "compativel", null, null, null));
        assertThat(motor.par(B, C).resultado()).isEqualTo("incompativel");
    }

    @Test
    void declaracaoSemSubstanciaNemClasseNuncaAtinge() {
        assertThat(motor(List.of(), declara(A, null, null, Severidade.CRITICA)).par(A, B).resultado())
                .isEqualTo("compativel");
    }

    @Test
    void aMaisGraveVenceEEmEmpateAReguaCuradaPrevalece() {
        IncompatibilidadeDaFds criticaDaFds = declara(A, 2, null, Severidade.CRITICA);
        IncompatibilidadeDaFds mediaDaFds = declara(A, 2, null, Severidade.MEDIA);

        assertThat(motor(List.of(regra(7, Severidade.MEDIA)), criticaDaFds).par(A, B))
                .isEqualTo(new AvaliacaoParView(A, B, "incompativel", "critica", "declarada por 10", null));
        assertThat(motor(List.of(regra(7, Severidade.ALTA)), mediaDaFds).par(A, B))
                .isEqualTo(new AvaliacaoParView(A, B, "incompativel", "alta", "regra 7", 7));
        assertThat(motor(List.of(regra(7, Severidade.ALTA)), declara(A, 2, null, Severidade.ALTA)).par(A, B))
                .isEqualTo(new AvaliacaoParView(A, B, "incompativel", "alta", "regra 7", 7));
    }

    @Test
    void entreDeclaracoesDeMesmaGravidadeEscolheEmOrdemEstavel() {
        IncompatibilidadeDaFds primeira = new IncompatibilidadeDaFds(A, 2, null, Severidade.ALTA, "a descrição");
        IncompatibilidadeDaFds segunda = new IncompatibilidadeDaFds(B, 1, null, Severidade.ALTA, "b descrição");

        assertThat(motor(List.of(), segunda, primeira).par(A, B).descricaoRisco()).isEqualTo("a descrição");
        assertThat(motor(List.of(), primeira, segunda).par(A, B).descricaoRisco()).isEqualTo("a descrição");
    }

    @Test
    void noEmpateAQueTemDescricaoVenceASemDescricao() {
        IncompatibilidadeDaFds sem = new IncompatibilidadeDaFds(A, 2, null, Severidade.ALTA, null);
        IncompatibilidadeDaFds com = new IncompatibilidadeDaFds(B, 1, null, Severidade.ALTA, "Gases tóxicos");

        assertThat(motor(List.of(), sem, com).par(A, B).descricaoRisco()).isEqualTo("Gases tóxicos");
        assertThat(motor(List.of(), com, sem).par(A, B).descricaoRisco()).isEqualTo("Gases tóxicos");
    }

    @Test
    void declaracaoSemDescricaoUsaOTextoPadrao() {
        for (String vazia : new String[] {null, "", "   "}) {
            IncompatibilidadeDaFds declarada = new IncompatibilidadeDaFds(A, 2, null, Severidade.MEDIA, vazia);

            assertThat(motor(List.of(), declarada).par(A, B).descricaoRisco())
                    .isEqualTo(MotorDeCompatibilidade.DECLARADA_NA_FDS);
        }
    }

    @Test
    void composicaoParcialComDeclaracaoQueCasaEIncompativelESemElaContinuaNaoAvaliada() {
        Map<Integer, ComposicaoDoProduto> parcial = Map.of(
                A, new ComposicaoDoProduto(Set.of(1), Set.of(2), true),
                B, new ComposicaoDoProduto(Set.of(2), Set.of(1), false));

        assertThat(new MotorDeCompatibilidade(parcial, List.of(), List.of(declara(A, 2, null, Severidade.MEDIA)))
                        .par(A, B).resultado())
                .isEqualTo("incompativel");
        assertThat(new MotorDeCompatibilidade(parcial, List.of(), List.of()).par(A, B).resultado())
                .isEqualTo("nao_avaliado");
    }

    @Test
    void semSubstanciaResolvidaNaoHaAvaliacaoMesmoComDeclaracao() {
        Map<Integer, ComposicaoDoProduto> semFds = Map.of(B, new ComposicaoDoProduto(Set.of(2), Set.of(1), false));

        AvaliacaoParView par = new MotorDeCompatibilidade(semFds, List.of(), List.of(declara(A, 2, null, Severidade.CRITICA)))
                .par(A, B);

        assertThat(par.resultado()).isEqualTo("nao_avaliado");
    }

    @Test
    void conjuntoAceitaIncompatibilidadeSemRegraNaOrdenacaoENaGravidade() {
        MotorDeCompatibilidade motor = motor(
                List.of(regra(7, Severidade.MEDIA)), declara(A, 2, null, Severidade.MEDIA), declara(B, 3, null, Severidade.CRITICA));

        AvaliacaoConjuntoView conjunto = motor.conjunto(List.of(A, B, C));

        assertThat(conjunto.status()).isEqualTo("critico");
        assertThat(conjunto.incompatibilidades()).extracting(AvaliacaoParView::resultado).containsOnly("incompativel");
        assertThat(conjunto.incompatibilidades()).extracting(AvaliacaoParView::regraId).containsExactlyInAnyOrder(7, null);
        assertThat(conjunto.incompatibilidades().get(0).severidade()).isEqualTo("critica");
    }
}
