package com.api.quimia.domain.safety;

import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.ATENCAO;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.COMPATIVEL;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.COMPOSICAO_INCOMPLETA;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.CRITICO;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.INCOMPATIVEL;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.NAO_AVALIADO;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.SEGURO;
import static com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade.SEM_COMPOSICAO;
import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import com.api.quimia.domain.safety.internal.model.ComposicaoDoProduto;
import com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade;
import com.api.quimia.domain.safety.internal.model.RegraDeIncompatibilidade;
import com.api.quimia.domain.safety.internal.model.Severidade;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * Propriedades que o motor tem de cumprir em qualquer cenário. Cada propriedade roda em
 * {@value #CENARIOS} cenários gerados por semente; a falha mostra a semente para reproduzir. Para explorar outras
 * sementes: `-Dmotor.semente.inicial=N`.
 */
class MotorDeCompatibilidadePropriedadesTest {
    private static final int CENARIOS = 3000;
    private static final long SEMENTE_INICIAL = Long.getLong("motor.semente.inicial", 20261019L);
    private static final List<String> SEVERIDADES = List.of("baixa", "media", "alta", "critica");

    private static void emCadaCenario(Consumer<CenarioDeCompatibilidade> propriedade) {
        LongStream.range(SEMENTE_INICIAL, SEMENTE_INICIAL + CENARIOS)
                .mapToObj(CenarioDeCompatibilidade::gerar)
                .forEach(propriedade);
    }

    private static List<int[]> pares(List<Integer> produtos) {
        List<int[]> pares = new ArrayList<>();
        for (int i = 0; i < produtos.size(); i++) {
            for (int j = i + 1; j < produtos.size(); j++) {
                pares.add(new int[] {produtos.get(i), produtos.get(j)});
            }
        }
        return pares;
    }

    /** Ordem de risco do par: compatível < não avaliado < incompatível baixa < ... < incompatível crítica. */
    private static int riscoDoPar(AvaliacaoParView par) {
        return switch (par.resultado()) {
            case COMPATIVEL -> 0;
            case NAO_AVALIADO -> 1;
            default -> 2 + SEVERIDADES.indexOf(par.severidade());
        };
    }

    private static int riscoDoConjunto(String status) {
        return List.of(SEGURO, NAO_AVALIADO, ATENCAO, CRITICO).indexOf(status);
    }

    @Test
    void resultadoDoParCoincideComAReferenciaPorForcaBruta() {
        emCadaCenario(cenario -> {
            MotorDeCompatibilidade motor = cenario.motor();
            for (int[] par : pares(cenario.produtos())) {
                AvaliacaoParView avaliacao = motor.par(par[0], par[1]);
                ComposicaoDoProduto a = cenario.composicao(par[0]);
                ComposicaoDoProduto b = cenario.composicao(par[1]);
                Optional<RegraDeIncompatibilidade> pior = cenario.piorRegraEntre(par[0], par[1]);
                String contexto = "semente %d, par %d-%d".formatted(cenario.semente(), par[0], par[1]);

                if (a.semSubstanciaResolvida() || b.semSubstanciaResolvida()) {
                    assertThat(avaliacao.resultado()).as(contexto).isEqualTo(NAO_AVALIADO);
                    assertThat(avaliacao.descricaoRisco()).as(contexto).isEqualTo(SEM_COMPOSICAO);
                } else if (pior.isPresent()) {
                    assertThat(avaliacao.resultado()).as(contexto).isEqualTo(INCOMPATIVEL);
                    assertThat(avaliacao.regraId()).as(contexto).isEqualTo(pior.get().id());
                    assertThat(avaliacao.severidade()).as(contexto).isEqualTo(pior.get().severidade().codigo());
                    assertThat(avaliacao.descricaoRisco()).as(contexto).isEqualTo(pior.get().descricaoRisco());
                } else if (a.temCompostoNaoResolvido() || b.temCompostoNaoResolvido()) {
                    assertThat(avaliacao.resultado()).as(contexto).isEqualTo(NAO_AVALIADO);
                    assertThat(avaliacao.descricaoRisco()).as(contexto).isEqualTo(COMPOSICAO_INCOMPLETA);
                } else {
                    assertThat(avaliacao.resultado()).as(contexto).isEqualTo(COMPATIVEL);
                    assertThat(avaliacao.descricaoRisco()).as(contexto).isNull();
                }
            }
        });
    }

    @Test
    void parEhSimetricoENormalizadoComCamposCoerentes() {
        emCadaCenario(cenario -> {
            MotorDeCompatibilidade motor = cenario.motor();
            for (int[] par : pares(cenario.produtos())) {
                AvaliacaoParView ida = motor.par(par[0], par[1]);
                AvaliacaoParView volta = motor.par(par[1], par[0]);
                String contexto = "semente %d, par %d-%d".formatted(cenario.semente(), par[0], par[1]);

                assertThat(volta).as(contexto).isEqualTo(ida);
                assertThat(ida.produtoIdA()).as(contexto).isEqualTo(Math.min(par[0], par[1]));
                assertThat(ida.produtoIdB()).as(contexto).isEqualTo(Math.max(par[0], par[1]));
                if (INCOMPATIVEL.equals(ida.resultado())) {
                    assertThat(ida.severidade()).as(contexto).isIn(SEVERIDADES);
                    assertThat(ida.regraId()).as(contexto).isNotNull();
                } else {
                    assertThat(ida.severidade()).as(contexto).isNull();
                    assertThat(ida.regraId()).as(contexto).isNull();
                }
            }
        });
    }

    @Test
    void conjuntoEhOAgregadoExatoDosPares() {
        emCadaCenario(cenario -> {
            MotorDeCompatibilidade motor = cenario.motor();
            AvaliacaoConjuntoView conjunto = motor.conjunto(cenario.produtos());
            List<AvaliacaoParView> todos = pares(cenario.produtos()).stream().map(par -> motor.par(par[0], par[1])).toList();
            List<AvaliacaoParView> incompativeis = todos.stream().filter(par -> INCOMPATIVEL.equals(par.resultado())).toList();
            long naoAvaliados = todos.stream().filter(par -> NAO_AVALIADO.equals(par.resultado())).count();
            String contexto = "semente " + cenario.semente();

            assertThat(conjunto.totalProdutos()).as(contexto).isEqualTo(cenario.produtos().size());
            assertThat(conjunto.paresAvaliados() + conjunto.paresNaoAvaliados()).as(contexto).isEqualTo(todos.size());
            assertThat(conjunto.paresNaoAvaliados()).as(contexto).isEqualTo(naoAvaliados);
            assertThat(conjunto.paresIncompativeis()).as(contexto).isEqualTo(incompativeis.size());
            assertThat(conjunto.incompatibilidades()).as(contexto).containsExactlyInAnyOrderElementsOf(incompativeis);

            int piorRisco = todos.stream().mapToInt(MotorDeCompatibilidadePropriedadesTest::riscoDoPar).max().orElse(0);
            String esperado = piorRisco >= 2 + SEVERIDADES.indexOf("alta") ? CRITICO
                    : piorRisco >= 2 ? ATENCAO
                    : piorRisco == 1 ? NAO_AVALIADO
                    : SEGURO;
            assertThat(conjunto.status()).as(contexto).isEqualTo(esperado);
            assertThat(conjunto.severidadeMaxima()).as(contexto)
                    .isEqualTo(piorRisco >= 2 ? SEVERIDADES.get(piorRisco - 2) : null);
        });
    }

    @Test
    void incompatibilidadesVemDaMaisGraveParaAMenosGraveSemEmpateAmbiguo() {
        emCadaCenario(cenario -> {
            List<AvaliacaoParView> lista = cenario.motor().conjunto(cenario.produtos()).incompatibilidades();
            for (int i = 1; i < lista.size(); i++) {
                AvaliacaoParView anterior = lista.get(i - 1);
                AvaliacaoParView atual = lista.get(i);
                int gravidadeAnterior = SEVERIDADES.indexOf(anterior.severidade());
                int gravidadeAtual = SEVERIDADES.indexOf(atual.severidade());
                String contexto = "semente %d, posição %d".formatted(cenario.semente(), i);
                assertThat(gravidadeAnterior).as(contexto).isGreaterThanOrEqualTo(gravidadeAtual);
                if (gravidadeAnterior == gravidadeAtual && anterior.regraId().equals(atual.regraId())) {
                    assertThat(anterior.produtoIdA() < atual.produtoIdA()
                                    || (anterior.produtoIdA().equals(atual.produtoIdA())
                                            && anterior.produtoIdB() < atual.produtoIdB()))
                            .as(contexto)
                            .isTrue();
                } else if (gravidadeAnterior == gravidadeAtual) {
                    assertThat(anterior.regraId()).as(contexto).isLessThan(atual.regraId());
                }
            }
        });
    }

    @Test
    void ordemRepeticaoENulosNaoMudamOConjunto() {
        emCadaCenario(cenario -> {
            MotorDeCompatibilidade motor = cenario.motor();
            AvaliacaoConjuntoView original = motor.conjunto(cenario.produtos());
            SplittableRandom aleatorio = new SplittableRandom(cenario.semente());
            List<Integer> baguncados = new ArrayList<>(cenario.produtos());
            baguncados.addAll(cenario.produtos().subList(0, 1 + aleatorio.nextInt(cenario.produtos().size())));
            baguncados.add(null);
            CenarioDeCompatibilidade.embaralhar(baguncados, aleatorio);

            assertThat(motor.conjunto(baguncados)).as("semente " + cenario.semente()).isEqualTo(original);
        });
    }

    @Test
    void ordemDasRegrasNaoMudaNada() {
        emCadaCenario(cenario -> {
            List<RegraDeIncompatibilidade> outraOrdem = new ArrayList<>(cenario.regras());
            CenarioDeCompatibilidade.embaralhar(outraOrdem, new SplittableRandom(~cenario.semente()));

            assertThat(cenario.comRegras(outraOrdem).motor().conjunto(cenario.produtos()))
                    .as("semente " + cenario.semente())
                    .isEqualTo(cenario.motor().conjunto(cenario.produtos()));
        });
    }

    @Test
    void novaRegraNuncaReduzORisco() {
        emCadaCenario(cenario -> {
            SplittableRandom aleatorio = new SplittableRandom(cenario.semente() * 31);
            List<RegraDeIncompatibilidade> mais = new ArrayList<>(cenario.regras());
            int id = mais.stream().mapToInt(RegraDeIncompatibilidade::id).max().orElse(0) + 1 + aleatorio.nextInt(3);
            mais.add(new RegraDeIncompatibilidade(
                    id, 1 + aleatorio.nextInt(10), null, null, 1 + aleatorio.nextInt(5),
                    Severidade.values()[aleatorio.nextInt(4)], "nova"));
            MotorDeCompatibilidade antes = cenario.motor();
            MotorDeCompatibilidade depois = cenario.comRegras(mais).motor();

            for (int[] par : pares(cenario.produtos())) {
                assertThat(riscoDoPar(depois.par(par[0], par[1])))
                        .as("semente %d, par %d-%d", cenario.semente(), par[0], par[1])
                        .isGreaterThanOrEqualTo(riscoDoPar(antes.par(par[0], par[1])));
            }
            assertThat(riscoDoConjunto(depois.conjunto(cenario.produtos()).status()))
                    .as("semente " + cenario.semente())
                    .isGreaterThanOrEqualTo(riscoDoConjunto(antes.conjunto(cenario.produtos()).status()));
        });
    }

    @Test
    void desativarRegraNuncaAumentaORisco() {
        emCadaCenario(cenario -> {
            if (cenario.regras().isEmpty()) {
                return;
            }
            int removida = new SplittableRandom(cenario.semente()).nextInt(cenario.regras().size());
            List<RegraDeIncompatibilidade> menos = new ArrayList<>(cenario.regras());
            menos.remove(removida);
            MotorDeCompatibilidade antes = cenario.motor();
            MotorDeCompatibilidade depois = cenario.comRegras(menos).motor();

            for (int[] par : pares(cenario.produtos())) {
                assertThat(riscoDoPar(depois.par(par[0], par[1])))
                        .as("semente %d, par %d-%d", cenario.semente(), par[0], par[1])
                        .isLessThanOrEqualTo(riscoDoPar(antes.par(par[0], par[1])));
            }
        });
    }

    @Test
    void produtoNovoNoConjuntoNuncaApagaRiscoExistente() {
        emCadaCenario(cenario -> {
            List<Integer> menos = cenario.produtos().subList(0, cenario.produtos().size() - 1);
            if (menos.size() < 2) {
                return;
            }
            MotorDeCompatibilidade motor = cenario.motor();
            AvaliacaoConjuntoView antes = motor.conjunto(menos);
            AvaliacaoConjuntoView depois = motor.conjunto(cenario.produtos());
            String contexto = "semente " + cenario.semente();

            assertThat(riscoDoConjunto(depois.status())).as(contexto).isGreaterThanOrEqualTo(riscoDoConjunto(antes.status()));
            assertThat(depois.incompatibilidades()).as(contexto).containsAll(antes.incompatibilidades());
        });
    }

    @Test
    void compostoNaoIdentificadoNuncaGeraCompativel() {
        emCadaCenario(cenario -> {
            Integer produto = cenario.produtos().get(0);
            ComposicaoDoProduto original = cenario.composicao(produto);
            CenarioDeCompatibilidade incompleto = cenario.comComposicao(
                    produto, new ComposicaoDoProduto(original.substancias(), original.classes(), true));
            MotorDeCompatibilidade antes = cenario.motor();
            MotorDeCompatibilidade depois = incompleto.motor();

            for (Integer outro : cenario.produtos().subList(1, cenario.produtos().size())) {
                AvaliacaoParView parAntes = antes.par(produto, outro);
                AvaliacaoParView parDepois = depois.par(produto, outro);
                String contexto = "semente %d, par %d-%d".formatted(cenario.semente(), produto, outro);
                assertThat(parDepois.resultado()).as(contexto).isNotEqualTo(COMPATIVEL);
                if (!COMPATIVEL.equals(parAntes.resultado())) {
                    assertThat(parDepois).as(contexto).isEqualTo(parAntes);
                }
            }
        });
    }

    @Test
    void seguroSoQuandoTodosOsParesSaoCompativeis() {
        emCadaCenario(cenario -> {
            MotorDeCompatibilidade motor = cenario.motor();
            if (!SEGURO.equals(motor.conjunto(cenario.produtos()).status())) {
                return;
            }
            for (int[] par : pares(cenario.produtos())) {
                assertThat(motor.par(par[0], par[1]).resultado())
                        .as("semente %d, par %d-%d", cenario.semente(), par[0], par[1])
                        .isEqualTo(COMPATIVEL);
            }
        });
    }

    @Test
    void cenariosCobremTodosOsResultados() {
        var resultados = new HashSet<String>();
        var status = new HashSet<String>();
        emCadaCenario(cenario -> {
            MotorDeCompatibilidade motor = cenario.motor();
            pares(cenario.produtos()).forEach(par -> resultados.add(motor.par(par[0], par[1]).resultado()));
            status.add(motor.conjunto(cenario.produtos()).status());
        });
        assertThat(resultados).containsExactlyInAnyOrder(COMPATIVEL, INCOMPATIVEL, NAO_AVALIADO);
        assertThat(status).containsExactlyInAnyOrder(SEGURO, ATENCAO, CRITICO, NAO_AVALIADO);
    }

    @Test
    void motorNaoDependeDaColecaoRecebida() {
        CenarioDeCompatibilidade cenario = CenarioDeCompatibilidade.gerar(SEMENTE_INICIAL);
        var regras = new ArrayList<>(cenario.regras());
        var composicoes = new java.util.HashMap<>(cenario.composicoes());
        MotorDeCompatibilidade motor = new MotorDeCompatibilidade(composicoes, regras);
        AvaliacaoConjuntoView antes = motor.conjunto(cenario.produtos());

        regras.clear();
        composicoes.clear();

        assertThat(motor.conjunto(cenario.produtos())).isEqualTo(antes);
    }
}
