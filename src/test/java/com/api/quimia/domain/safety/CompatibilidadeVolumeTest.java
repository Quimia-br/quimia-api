package com.api.quimia.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Cômodo no limite (20 produtos, 190 pares) contra uma tabela grande de regras: o custo de consulta ao banco não
 * cresce com o número de pares nem com regras que não podem casar.
 */
@SpringBootTest(
        classes = com.api.quimia.Application.class,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class CompatibilidadeVolumeTest {
    private static final int PRODUTOS = 20;
    private static final int SUBSTANCIAS = 60;
    private static final int REGRAS = 1000;
    /** Compostos, classes das substâncias e regras candidatas. */
    private static final int CONSULTAS_POR_AVALIACAO = 3;
    private static final Duration PRAZO = Duration.ofSeconds(3);

    @Autowired
    private CompatibilidadeGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory emf;

    private final List<Integer> produtos = new ArrayList<>();

    @BeforeEach
    void seed() {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        SplittableRandom aleatorio = new SplittableRandom(42);
        int marca = catalogo.marca("Marca", null);
        List<Integer> substancias = new ArrayList<>();
        for (int i = 0; i < SUBSTANCIAS; i++) {
            substancias.add(catalogo.substancia("S" + i, "V-" + i, null));
        }
        int classe = catalogo.classe("Oxidantes", null);
        substancias.subList(0, 10).forEach(substancia -> catalogo.substanciaClasse(substancia, classe));
        for (int i = 0; i < PRODUTOS; i++) {
            int produto = catalogo.produto("P" + i, marca, "limpeza_geral", null, null);
            int fds = catalogo.fds(produto, "1", LocalDate.of(2026, 1, 1), true, null);
            for (int c = 0; c < 4; c++) {
                catalogo.composto(fds, substancias.get(aleatorio.nextInt(30)), "1.00", "2.00", "%");
            }
            produtos.add(produto);
        }
        List<Object[]> regras = new ArrayList<>();
        for (int i = 0; i < REGRAS; i++) {
            regras.add(new Object[] {
                substancias.get(aleatorio.nextInt(SUBSTANCIAS)), substancias.get(aleatorio.nextInt(SUBSTANCIAS)),
                List.of("baixa", "media", "alta", "critica").get(aleatorio.nextInt(4))
            });
        }
        jdbc.batchUpdate(
                "INSERT INTO incompatibilidade_regra (id_substancia_a, id_substancia_b, severidade, descricao_risco, ativo)"
                        + " VALUES (?, ?, ?, 'volume', TRUE)",
                regras);
    }

    @Test
    void comodoCheioAvaliaEmPoucasConsultasEDentroDoPrazo() {
        Statistics estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        gateway.avaliarConjunto(produtos);
        estatisticas.clear();

        long inicio = System.nanoTime();
        AvaliacaoConjuntoView conjunto = gateway.avaliarConjunto(produtos);
        Duration tempo = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(conjunto.totalProdutos()).isEqualTo(PRODUTOS);
        assertThat(conjunto.paresAvaliados() + conjunto.paresNaoAvaliados()).isEqualTo(PRODUTOS * (PRODUTOS - 1) / 2);
        assertThat(conjunto.paresIncompativeis()).isPositive();
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(CONSULTAS_POR_AVALIACAO);
        assertThat(tempo).isLessThan(PRAZO);
    }

    @Test
    void conjuntoVazioNaoConsultaOBanco() {
        Statistics estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        gateway.avaliarConjunto(List.of());

        assertThat(estatisticas.getPrepareStatementCount()).isZero();
    }

    @Test
    void semSubstanciaIdentificadaSoConsultaOsCompostos() {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        int marca = catalogo.marca("Sem composição", null);
        int semFds = catalogo.produto("Sem FDS", marca, "limpeza_geral", null, null);
        int naoIdentificado = catalogo.produto("Não identificado", marca, "limpeza_geral", null, null);
        catalogo.composto(catalogo.fds(naoIdentificado, "1", LocalDate.of(2026, 1, 1), true, null), null, "1.00", "2.00", "%");
        Statistics estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        AvaliacaoConjuntoView conjunto = gateway.avaliarConjunto(List.of(semFds, naoIdentificado));

        assertThat(conjunto.paresNaoAvaliados()).isEqualTo(1);
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void parAvaliaEmPoucasConsultas() {
        Statistics estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        gateway.avaliarPar(produtos.get(0), produtos.get(1));

        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(CONSULTAS_POR_AVALIACAO);
    }
}
