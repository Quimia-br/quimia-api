package com.api.quimia.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = com.api.quimia.Application.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class CompatibilidadeGatewayTest {
    @Autowired
    private CompatibilidadeGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    private TestCatalogo catalogo;
    private int marca;
    private int hipoclorito;
    private int amonia;
    private int agua;

    @BeforeEach
    void seed() {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        marca = catalogo.marca("Marca Teste", null);
        hipoclorito = catalogo.substancia("Hipoclorito de sódio", "7681-52-9", "Alvejante");
        amonia = catalogo.substancia("Amônia", "7664-41-7", "Alcalino volátil");
        agua = catalogo.substancia("Água", "7732-18-5", null);
    }

    /** Produto com FDS ativa cujos compostos resolvem as substâncias informadas. */
    private int produto(String nome, Integer... substancias) {
        int produto = catalogo.produto(nome, marca, "limpeza_geral", null, null);
        int fds = catalogo.fds(produto, "1", LocalDate.of(2026, 1, 1), true, null);
        for (Integer substancia : substancias) {
            catalogo.composto(fds, substancia, "1.00", "5.00", "%");
        }
        return produto;
    }

    private int semFds(String nome) {
        return catalogo.produto(nome, marca, "limpeza_geral", null, null);
    }

    private void compostoNaoResolvido(int produto) {
        int fds = jdbc.queryForObject("SELECT id FROM fds WHERE id_produto = ?", Integer.class, produto);
        catalogo.composto(fds, null, "1.00", "2.00", "%");
    }

    @Test
    void parSemRegraEhCompativel() {
        int a = produto("Detergente", agua);
        int b = produto("Alvejante", hipoclorito);

        AvaliacaoParView par = gateway.avaliarPar(a, b);

        assertThat(par.resultado()).isEqualTo("compativel");
        assertThat(par.severidade()).isNull();
        assertThat(par.regraId()).isNull();
        assertThat(par.produtoIdA()).isEqualTo(Math.min(a, b));
        assertThat(par.produtoIdB()).isEqualTo(Math.max(a, b));
    }

    @Test
    void regraPorSubstanciaDaParIncompativel() {
        int regra = catalogo.regra(hipoclorito, null, amonia, null, "critica", "Libera gás tóxico (cloramina)", true);
        int alvejante = produto("Alvejante", hipoclorito);
        int limpaVidros = produto("Limpa vidros", amonia);

        AvaliacaoParView par = gateway.avaliarPar(alvejante, limpaVidros);

        assertThat(par.resultado()).isEqualTo("incompativel");
        assertThat(par.severidade()).isEqualTo("critica");
        assertThat(par.descricaoRisco()).isEqualTo("Libera gás tóxico (cloramina)");
        assertThat(par.regraId()).isEqualTo(regra);
    }

    @Test
    void ordemDosIdsNaoMudaOResultado() {
        catalogo.regra(hipoclorito, null, amonia, null, "alta", "Risco", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);

        assertThat(gateway.avaliarPar(a, b)).isEqualTo(gateway.avaliarPar(b, a));
    }

    @Test
    void idsIguaisLancamProdutosIguais() {
        int a = produto("A", agua);

        assertThatThrownBy(() -> gateway.avaliarPar(a, a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("produtos_iguais");
    }

    @Test
    void regraPorClasseCasaOsDoisLados() {
        int alvejanteClasse = catalogo.classe("Alvejante clorado", null);
        int acidoClasse = catalogo.classe("Ácido", null);
        int acido = catalogo.substancia("Ácido muriático", "7647-01-0", null);
        catalogo.substanciaClasse(hipoclorito, alvejanteClasse);
        catalogo.substanciaClasse(acido, acidoClasse);
        catalogo.regra(null, alvejanteClasse, null, acidoClasse, "critica", "Libera cloro gasoso", true);
        int a = produto("Alvejante", hipoclorito);
        int b = produto("Desincrustante", acido);

        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo("incompativel");
        assertThat(gateway.avaliarPar(b, a).severidade()).isEqualTo("critica");
    }

    @Test
    void regraMistaSubstanciaDeUmLadoEClasseDoOutro() {
        int basica = catalogo.classe("Base", null);
        catalogo.substanciaClasse(amonia, basica);
        catalogo.regra(hipoclorito, null, null, basica, "alta", "Cloraminas", true);
        int a = produto("Alvejante", hipoclorito);
        int b = produto("Limpa vidros", amonia);

        assertThat(gateway.avaliarPar(a, b).severidade()).isEqualTo("alta");
    }

    @Test
    void variasRegrasDevolvemAMaisGraveEEmpateFicaComAMenorId() {
        catalogo.regra(hipoclorito, null, amonia, null, "media", "Regra média", true);
        int primeiraAlta = catalogo.regra(hipoclorito, null, amonia, null, "alta", "Regra alta 1", true);
        catalogo.regra(hipoclorito, null, amonia, null, "alta", "Regra alta 2", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);

        AvaliacaoParView par = gateway.avaliarPar(a, b);

        assertThat(par.severidade()).isEqualTo("alta");
        assertThat(par.regraId()).isEqualTo(primeiraAlta);
    }

    @Test
    void criticaVenceAltaMesmoComIdMaior() {
        catalogo.regra(hipoclorito, null, amonia, null, "alta", "Alta", true);
        int critica = catalogo.regra(hipoclorito, null, amonia, null, "critica", "Crítica", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);

        assertThat(gateway.avaliarPar(a, b).regraId()).isEqualTo(critica);
    }

    @Test
    void regraInativaEhIgnorada() {
        catalogo.regra(hipoclorito, null, amonia, null, "critica", "Inativa", false);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);

        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo("compativel");
    }

    @Test
    void duasFdsAtivasDoMesmoProdutoSomamAComposicao() {
        catalogo.regra(hipoclorito, null, amonia, null, "alta", "Risco", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", agua);
        int segundaFds = catalogo.fds(b, "2", LocalDate.of(2026, 2, 1), true, null);
        catalogo.composto(segundaFds, amonia, "1.00", "2.00", "%");

        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo("incompativel");
    }

    @Test
    void produtoSemFdsEhNaoAvaliado() {
        int semFds = semFds("Sem FDS");
        int b = produto("B", agua);

        AvaliacaoParView par = gateway.avaliarPar(semFds, b);

        assertThat(par.resultado()).isEqualTo("nao_avaliado");
        assertThat(par.descricaoRisco()).isEqualTo("Produto sem composição resolvida");
        assertThat(par.severidade()).isNull();
    }

    @Test
    void fdsInativaNaoContaComoComposicao() {
        int a = catalogo.produto("A", marca, "limpeza_geral", null, null);
        int fdsInativa = catalogo.fds(a, "1", LocalDate.of(2026, 1, 1), false, null);
        catalogo.composto(fdsInativa, hipoclorito, "1.00", "2.00", "%");
        int b = produto("B", agua);

        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo("nao_avaliado");
    }

    @Test
    void apenasCompostosNaoResolvidosEhNaoAvaliado() {
        int a = catalogo.produto("A", marca, "limpeza_geral", null, null);
        int fds = catalogo.fds(a, "1", LocalDate.of(2026, 1, 1), true, null);
        catalogo.composto(fds, null, "1.00", "2.00", "%");
        int b = produto("B", agua);

        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo("nao_avaliado");
    }

    @Test
    void composicaoParcialSemRegraEhNaoAvaliadoENaoCompativel() {
        int a = produto("A", agua);
        compostoNaoResolvido(a); // composto que o parser não identificou
        int b = produto("B", hipoclorito);

        AvaliacaoParView par = gateway.avaliarPar(a, b);

        assertThat(par.resultado()).isEqualTo("nao_avaliado");
        assertThat(par.descricaoRisco()).isEqualTo("Composição incompleta: há compostos não identificados");
    }

    @Test
    void composicaoParcialComRegraCasandoContinuaIncompativel() {
        catalogo.regra(hipoclorito, null, amonia, null, "critica", "Cloraminas", true);
        int a = produto("A", amonia);
        compostoNaoResolvido(a);
        int b = produto("B", hipoclorito);

        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo("incompativel");
    }

    @Test
    void conjuntoCriticoQuandoHaParCriticoOuAlto() {
        catalogo.regra(hipoclorito, null, amonia, null, "alta", "Risco alto", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);
        int c = produto("C", agua);

        AvaliacaoConjuntoView conjunto = gateway.avaliarConjunto(List.of(a, b, c));

        assertThat(conjunto.status()).isEqualTo("critico");
        assertThat(conjunto.totalProdutos()).isEqualTo(3);
        assertThat(conjunto.paresAvaliados()).isEqualTo(3);
        assertThat(conjunto.paresIncompativeis()).isEqualTo(1);
        assertThat(conjunto.severidadeMaxima()).isEqualTo("alta");
        assertThat(conjunto.incompatibilidades()).hasSize(1);
    }

    @Test
    void conjuntoAtencaoQuandoOPiorParEhMediaOuBaixa() {
        catalogo.regra(hipoclorito, null, amonia, null, "media", "Risco médio", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);

        assertThat(gateway.avaliarConjunto(List.of(a, b)).status()).isEqualTo("atencao");
    }

    @Test
    void conjuntoSeguroQuandoTodosOsParesSaoCompativeis() {
        int a = produto("A", agua);
        int b = produto("B", hipoclorito);

        AvaliacaoConjuntoView conjunto = gateway.avaliarConjunto(List.of(a, b));

        assertThat(conjunto.status()).isEqualTo("seguro");
        assertThat(conjunto.severidadeMaxima()).isNull();
        assertThat(conjunto.incompatibilidades()).isEmpty();
    }

    @Test
    void conjuntoNaoAvaliadoQuandoAlgumParNaoPodeSerAvaliado() {
        int semFds = semFds("Sem FDS");
        int b = produto("B", agua);

        AvaliacaoConjuntoView conjunto = gateway.avaliarConjunto(List.of(semFds, b));

        assertThat(conjunto.status()).isEqualTo("nao_avaliado");
        assertThat(conjunto.paresNaoAvaliados()).isEqualTo(1);
    }

    @Test
    void incompativelPesaMaisQueNaoAvaliado() {
        catalogo.regra(hipoclorito, null, amonia, null, "critica", "Risco", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);
        int semFds = semFds("Sem FDS");

        assertThat(gateway.avaliarConjunto(List.of(a, b, semFds)).status()).isEqualTo("critico");
    }

    @Test
    void conjuntoComUmProdutoOuVazioEhNaoAvaliadoComZeros() {
        int a = produto("A", agua);

        AvaliacaoConjuntoView um = gateway.avaliarConjunto(List.of(a));
        AvaliacaoConjuntoView vazio = gateway.avaliarConjunto(List.of());

        assertThat(um.status()).isEqualTo("nao_avaliado");
        assertThat(um.totalProdutos()).isEqualTo(1);
        assertThat(um.paresAvaliados()).isZero();
        assertThat(vazio.status()).isEqualTo("nao_avaliado");
        assertThat(vazio.totalProdutos()).isZero();
    }

    @Test
    void idsRepetidosENulosContamUmaVez() {
        int a = produto("A", agua);
        int b = produto("B", hipoclorito);

        AvaliacaoConjuntoView conjunto = gateway.avaliarConjunto(Arrays.asList(a, b, a, null));

        assertThat(conjunto.totalProdutos()).isEqualTo(2);
        assertThat(conjunto.paresAvaliados()).isEqualTo(1);
    }

    @Test
    void incompatibilidadesVemDoMaisGraveParaOMenosGrave() {
        catalogo.regra(hipoclorito, null, amonia, null, "media", "Média", true);
        int outro = catalogo.substancia("Ácido muriático", "7647-01-0", null);
        catalogo.regra(hipoclorito, null, outro, null, "critica", "Crítica", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);
        int c = produto("C", outro);

        List<AvaliacaoParView> ordem = gateway.avaliarConjunto(List.of(a, b, c)).incompatibilidades();

        assertThat(ordem).extracting(AvaliacaoParView::severidade).containsExactly("critica", "media");
    }
}
