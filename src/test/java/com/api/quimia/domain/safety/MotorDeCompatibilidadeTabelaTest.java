package com.api.quimia.domain.safety;

import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.COMPATIVEL;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.COMPOSICAO_INCOMPLETA;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.CRITICO;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.INCOMPATIVEL;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.NAO_AVALIADO;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.SEM_COMPOSICAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import com.api.quimia.domain.safety.internal.model.ComposicaoDoProduto;
import com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade;
import com.api.quimia.domain.safety.internal.model.RegraDeIncompatibilidade;
import com.api.quimia.domain.safety.internal.model.Severidade;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tabela de decisão completa de um par: estado da composição de cada produto × tipo de regra. Os ids de substância
 * e de classe se cruzam de propósito: o produto 10 tem a substância 1 e a classe 2; o produto 20, a substância 2 e a
 * classe 1.
 */
class MotorDeCompatibilidadeTabelaTest {
    private static final int PRODUTO_A = 10;
    private static final int PRODUTO_B = 20;

    enum Composicao {
        SEM_FDS,
        SO_NAO_IDENTIFICADO,
        PARCIAL,
        COMPLETA;

        boolean temSubstancia() {
            return this == PARCIAL || this == COMPLETA;
        }

        ComposicaoDoProduto montar(int substancia, int classe) {
            return switch (this) {
                case SEM_FDS -> null;
                case SO_NAO_IDENTIFICADO -> new ComposicaoDoProduto(Set.of(), Set.of(), true);
                case PARCIAL -> new ComposicaoDoProduto(Set.of(substancia), Set.of(classe), true);
                case COMPLETA -> new ComposicaoDoProduto(Set.of(substancia), Set.of(classe), false);
            };
        }
    }

    enum Regra {
        NENHUMA(null),
        SUBSTANCIA_COM_SUBSTANCIA(regra(1, null, 2, null)),
        CLASSE_COM_CLASSE(regra(null, 2, null, 1)),
        SUBSTANCIA_COM_CLASSE(regra(1, null, null, 1)),
        LADOS_INVERTIDOS(regra(2, null, 1, null)),
        OS_DOIS_LADOS_NO_MESMO_PRODUTO(regra(1, null, null, 2));

        private final RegraDeIncompatibilidade regra;

        Regra(RegraDeIncompatibilidade regra) {
            this.regra = regra;
        }

        boolean casaOPar() {
            return this != NENHUMA && this != OS_DOIS_LADOS_NO_MESMO_PRODUTO;
        }

        private static RegraDeIncompatibilidade regra(Integer substanciaA, Integer classeA, Integer substanciaB, Integer classeB) {
            return new RegraDeIncompatibilidade(7, substanciaA, classeA, substanciaB, classeB, Severidade.MEDIA, "risco");
        }
    }

    static Stream<Arguments> combinacoes() {
        return Arrays.stream(Composicao.values())
                .flatMap(a -> Arrays.stream(Composicao.values())
                        .flatMap(b -> Arrays.stream(Regra.values()).map(regra -> Arguments.of(a, b, regra))));
    }

    @ParameterizedTest(name = "A={0}, B={1}, regra={2}")
    @MethodSource("combinacoes")
    void decisaoDoPar(Composicao a, Composicao b, Regra regra) {
        Map<Integer, ComposicaoDoProduto> composicoes = new HashMap<>();
        if (a.montar(1, 2) != null) {
            composicoes.put(PRODUTO_A, a.montar(1, 2));
        }
        if (b.montar(2, 1) != null) {
            composicoes.put(PRODUTO_B, b.montar(2, 1));
        }
        List<RegraDeIncompatibilidade> regras = regra.regra == null ? List.of() : List.of(regra.regra);

        AvaliacaoParView par = new MotorDeCompatibilidade(composicoes, regras).par(PRODUTO_B, PRODUTO_A);

        assertThat(par.produtoIdA()).isEqualTo(PRODUTO_A);
        assertThat(par.produtoIdB()).isEqualTo(PRODUTO_B);
        if (!a.temSubstancia() || !b.temSubstancia()) {
            assertThat(par).isEqualTo(new AvaliacaoParView(PRODUTO_A, PRODUTO_B, NAO_AVALIADO, null, SEM_COMPOSICAO, null));
        } else if (regra.casaOPar()) {
            assertThat(par).isEqualTo(new AvaliacaoParView(PRODUTO_A, PRODUTO_B, INCOMPATIVEL, "media", "risco", 7));
        } else if (a == Composicao.PARCIAL || b == Composicao.PARCIAL) {
            assertThat(par).isEqualTo(new AvaliacaoParView(PRODUTO_A, PRODUTO_B, NAO_AVALIADO, null, COMPOSICAO_INCOMPLETA, null));
        } else {
            assertThat(par).isEqualTo(new AvaliacaoParView(PRODUTO_A, PRODUTO_B, COMPATIVEL, null, null, null));
        }
    }

    private static MotorDeCompatibilidade motorComRegras(RegraDeIncompatibilidade... regras) {
        return new MotorDeCompatibilidade(
                Map.of(
                        PRODUTO_A, new ComposicaoDoProduto(Set.of(1), Set.of(2), false),
                        PRODUTO_B, new ComposicaoDoProduto(Set.of(2), Set.of(1), false)),
                List.of(regras));
    }

    private static RegraDeIncompatibilidade substancias(int id, Severidade severidade) {
        return new RegraDeIncompatibilidade(id, 1, null, 2, null, severidade, "regra " + id);
    }

    @Test
    void maisGraveVenceMesmoComIdMaiorEEmQualquerPosicao() {
        AvaliacaoParView par = motorComRegras(
                        substancias(1, Severidade.BAIXA),
                        substancias(9, Severidade.CRITICA),
                        substancias(5, Severidade.ALTA))
                .par(PRODUTO_A, PRODUTO_B);

        assertThat(par.regraId()).isEqualTo(9);
        assertThat(par.severidade()).isEqualTo("critica");
        assertThat(par.descricaoRisco()).isEqualTo("regra 9");
    }

    @Test
    void maisGraveVenceMesmoComIdMenorDepoisDeUmaMaisLeve() {
        AvaliacaoParView par = motorComRegras(substancias(8, Severidade.BAIXA), substancias(3, Severidade.ALTA))
                .par(PRODUTO_A, PRODUTO_B);

        assertThat(par.regraId()).isEqualTo(3);
    }

    @Test
    void empateDeSeveridadeFicaComAMenorIdEmQualquerOrdem() {
        assertThat(motorComRegras(substancias(8, Severidade.ALTA), substancias(3, Severidade.ALTA))
                        .par(PRODUTO_A, PRODUTO_B).regraId())
                .isEqualTo(3);
        assertThat(motorComRegras(substancias(3, Severidade.ALTA), substancias(8, Severidade.ALTA))
                        .par(PRODUTO_A, PRODUTO_B).regraId())
                .isEqualTo(3);
    }

    @Test
    void ladoSemSubstanciaNemClasseNuncaCasa() {
        AvaliacaoParView par = motorComRegras(new RegraDeIncompatibilidade(1, null, null, 2, null, Severidade.CRITICA, "x"))
                .par(PRODUTO_A, PRODUTO_B);

        assertThat(par.resultado()).isEqualTo(COMPATIVEL);
    }

    @Test
    void ladoComSubstanciaIgnoraAClasseDoMesmoLado() {
        AvaliacaoParView par = motorComRegras(new RegraDeIncompatibilidade(1, 99, 2, null, 1, Severidade.CRITICA, "x"))
                .par(PRODUTO_A, PRODUTO_B);

        assertThat(par.resultado()).isEqualTo(COMPATIVEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"extrema", "CRITICA", "", " alta"})
    void severidadeDesconhecidaValeComoCriticaENaoDerrubaOMotor(String codigo) {
        RegraDeIncompatibilidade regra = new RegraDeIncompatibilidade(
                1, 1, null, 2, null, Severidade.doCodigo(codigo), "dado corrompido");
        MotorDeCompatibilidade motor = motorComRegras(regra);

        assertThat(motor.par(PRODUTO_A, PRODUTO_B).severidade()).isEqualTo("critica");
        assertThat(motor.conjunto(List.of(PRODUTO_A, PRODUTO_B)).status()).isEqualTo(CRITICO);
    }

    @Test
    void severidadeNulaValeComoCritica() {
        assertThat(Severidade.doCodigo(null)).isEqualTo(Severidade.CRITICA);
    }

    @ParameterizedTest
    @EnumSource(Severidade.class)
    void cadaCodigoConhecidoVoltaParaASuaSeveridade(Severidade severidade) {
        assertThat(Severidade.doCodigo(severidade.codigo())).isEqualTo(severidade);
        assertThat(severidade.critica()).isEqualTo(severidade == Severidade.ALTA || severidade == Severidade.CRITICA);
    }

    @Test
    void parComIdsIguaisOuNulosEhRecusado() {
        MotorDeCompatibilidade motor = motorComRegras();

        assertThatThrownBy(() -> motor.par(PRODUTO_A, PRODUTO_A)).hasMessage("produtos_iguais");
        assertThatThrownBy(() -> motor.par(null, PRODUTO_A)).hasMessage("produtos_iguais");
        assertThatThrownBy(() -> motor.par(PRODUTO_A, null)).hasMessage("produtos_iguais");
    }

    @Test
    void idsNosExtremosDoIntNaoTransbordam() {
        MotorDeCompatibilidade motor = new MotorDeCompatibilidade(
                Map.of(
                        Integer.MIN_VALUE, new ComposicaoDoProduto(Set.of(1), Set.of(), false),
                        Integer.MAX_VALUE, new ComposicaoDoProduto(Set.of(2), Set.of(), false)),
                List.of(substancias(1, Severidade.MEDIA)));

        AvaliacaoParView par = motor.par(Integer.MAX_VALUE, Integer.MIN_VALUE);

        assertThat(par.produtoIdA()).isEqualTo(Integer.MIN_VALUE);
        assertThat(par.produtoIdB()).isEqualTo(Integer.MAX_VALUE);
        assertThat(par.resultado()).isEqualTo(INCOMPATIVEL);
    }

    @Test
    void conjuntoVazioOuComUmProdutoNaoFormaPar() {
        MotorDeCompatibilidade motor = motorComRegras(substancias(1, Severidade.CRITICA));
        List<Integer> soNulos = new ArrayList<>();
        soNulos.add(null);
        soNulos.add(null);

        assertThat(motor.conjunto(List.of()))
                .isEqualTo(new AvaliacaoConjuntoView(NAO_AVALIADO, 0, 0, 0, 0, null, List.of()));
        assertThat(motor.conjunto(List.of(PRODUTO_A, PRODUTO_A)))
                .isEqualTo(new AvaliacaoConjuntoView(NAO_AVALIADO, 1, 0, 0, 0, null, List.of()));
        assertThat(motor.conjunto(soNulos))
                .isEqualTo(new AvaliacaoConjuntoView(NAO_AVALIADO, 0, 0, 0, 0, null, List.of()));
    }

    @Test
    void composicaoNaoAceitaMudancaDepoisDeCriada() {
        var substancias = new java.util.HashSet<>(Set.of(1));
        ComposicaoDoProduto composicao = new ComposicaoDoProduto(substancias, Set.of(), false);
        substancias.clear();

        assertThat(composicao.substancias()).containsExactly(1);
        assertThatThrownBy(() -> composicao.classes().add(3)).isInstanceOf(UnsupportedOperationException.class);
    }
}
