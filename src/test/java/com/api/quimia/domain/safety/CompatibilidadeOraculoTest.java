package com.api.quimia.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Compara o gateway com um oráculo escrito só em SQL, independente do motor Java. O oráculo segue
 * `fn_match_produtos` (que desde 2026-10-27 também considera as incompatibilidades declaradas na seção 10 das FDS) e
 * a decisão R4 da API (composição parcial sem regra nem declaração é `nao_avaliado`). Os cenários são gerados por
 * semente e gravados no banco: FDS ativas e inativas, mais de uma FDS por produto, compostos não identificados, regras
 * ativas e inativas por substância e por classe, e incompatibilidades declaradas pelas FDS. Roda no H2 e no
 * PostgreSQL.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class CompatibilidadeOraculoTest {
    private static final int CENARIOS = 40;
    private static final long SEMENTE_INICIAL = Long.getLong("oraculo.semente.inicial", 7_331L);
    private static final List<String> SEVERIDADES = List.of("baixa", "media", "alta", "critica");

    private static final String COMPOSTO_ATIVO =
            "SELECT 1 FROM fds f JOIN fds_composto c ON c.id_fds = f.id WHERE f.id_produto = :PRODUTO AND f.ativo = TRUE";
    private static final String PIOR_REGRA = """
            SELECT r.id, r.severidade, r.descricao_risco
            FROM incompatibilidade_regra r
            WHERE r.ativo = TRUE
              AND ((%1$s AND %2$s) OR (%3$s AND %4$s))
            ORDER BY CASE r.severidade
                       WHEN 'critica' THEN 1 WHEN 'alta' THEN 2 WHEN 'media' THEN 3 WHEN 'baixa' THEN 4 END,
                     r.id
            """.formatted(lado("a", "x"), lado("b", "y"), lado("a", "y"), lado("b", "x"));

    private static final String RANK_SEVERIDADE =
            "CASE %s WHEN 'critica' THEN 1 WHEN 'alta' THEN 2 WHEN 'media' THEN 3 WHEN 'baixa' THEN 4 END";
    /** Incompatibilidade declarada por uma FDS ativa de `:x` ou `:y` que atinge o outro produto do par. */
    private static final String PIOR_DECLARADA = """
            SELECT i.severidade, i.descricao_risco
            FROM fds f JOIN fds_incompatibilidade i ON i.id_fds = f.id
            WHERE f.ativo = TRUE
              AND ((f.id_produto = :x AND %1$s) OR (f.id_produto = :y AND %2$s))
            ORDER BY %3$s, CASE WHEN i.descricao_risco IS NULL OR TRIM(i.descricao_risco) = '' THEN 1 ELSE 0 END,
                     i.descricao_risco
            """.formatted(atinge("y"), atinge("x"), RANK_SEVERIDADE.formatted("i.severidade"));

    @Autowired
    private CompatibilidadeGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NamedParameterJdbcTemplate sql;

    private TestCatalogo catalogo;

    /** Lado `a` ou `b` da regra presente nas FDS ativas do produto `:x` ou `:y`. */
    private static String lado(String lado, String produto) {
        String comClasse = COMPOSTO_ATIVO.replace(
                " WHERE", " JOIN substancia_classe_quimica s ON s.id_substancia = c.id_substancia WHERE");
        return ("((r.id_substancia_LADO IS NOT NULL AND EXISTS (" + COMPOSTO_ATIVO
                        + " AND c.id_substancia = r.id_substancia_LADO))"
                        + " OR (r.id_classe_LADO IS NOT NULL AND EXISTS (" + comClasse
                        + " AND s.id_classe_quimica = r.id_classe_LADO)))")
                .replace("LADO", lado)
                .replace("PRODUTO", produto);
    }

    /** A declaração `i` casa com uma substância ou classe das FDS ativas do produto `:produto`. */
    private static String atinge(String produto) {
        String comClasse = COMPOSTO_ATIVO.replace(
                " WHERE", " JOIN substancia_classe_quimica s ON s.id_substancia = c.id_substancia WHERE");
        return ("(EXISTS (" + COMPOSTO_ATIVO + " AND c.id_substancia = i.id_substancia)"
                        + " OR EXISTS (" + comClasse + " AND s.id_classe_quimica = i.id_classe_quimica))")
                .replace("PRODUTO", produto);
    }

    @BeforeEach
    void limpar() {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
    }

    private boolean existe(String condicao, int produto) {
        return Boolean.TRUE.equals(sql.queryForObject(
                "SELECT CASE WHEN EXISTS (" + COMPOSTO_ATIVO.replace("PRODUTO", "p") + " AND " + condicao + ") THEN TRUE ELSE FALSE END",
                Map.of("p", produto),
                Boolean.class));
    }

    /** Resultado esperado do par, só com SQL. */
    private AvaliacaoParView oraculo(int x, int y) {
        int a = Math.min(x, y);
        int b = Math.max(x, y);
        if (!existe("c.id_substancia IS NOT NULL", a) || !existe("c.id_substancia IS NOT NULL", b)) {
            return new AvaliacaoParView(a, b, "nao_avaliado", null, "Produto sem composição resolvida", null);
        }
        List<Map<String, Object>> regras = sql.queryForList(PIOR_REGRA, Map.of("x", a, "y", b));
        List<Map<String, Object>> declaradas = sql.queryForList(PIOR_DECLARADA, Map.of("x", a, "y", b));
        // Gravidade igual: a regra curada vence (id_regra NULLS LAST na função do banco).
        if (!regras.isEmpty() && (declaradas.isEmpty() || posicao(regras.get(0)) <= posicao(declaradas.get(0)))) {
            Map<String, Object> pior = regras.get(0);
            return new AvaliacaoParView(
                    a, b, "incompativel", (String) pior.get("severidade"), (String) pior.get("descricao_risco"),
                    ((Number) pior.get("id")).intValue());
        }
        if (!declaradas.isEmpty()) {
            Map<String, Object> pior = declaradas.get(0);
            String descricao = (String) pior.get("descricao_risco");
            return new AvaliacaoParView(
                    a, b, "incompativel", (String) pior.get("severidade"),
                    descricao == null || descricao.isBlank() ? "Incompatibilidade declarada na FDS do produto" : descricao,
                    null);
        }
        if (existe("c.id_substancia IS NULL", a) || existe("c.id_substancia IS NULL", b)) {
            return new AvaliacaoParView(a, b, "nao_avaliado", null, "Composição incompleta: há compostos não identificados", null);
        }
        return new AvaliacaoParView(a, b, "compativel", null, null, null);
    }

    private static int posicao(Map<String, Object> linha) {
        return SEVERIDADES.size() - SEVERIDADES.indexOf((String) linha.get("severidade"));
    }

    /** Grava um cenário aleatório e devolve os produtos. */
    private List<Integer> gravarCenario(long semente) {
        SplittableRandom aleatorio = new SplittableRandom(semente);
        int marca = catalogo.marca("Marca " + semente, null);
        List<Integer> substancias = new ArrayList<>();
        int totalSubstancias = 2 + aleatorio.nextInt(8);
        for (int i = 0; i < totalSubstancias; i++) {
            substancias.add(catalogo.substancia("Substância " + i, "S" + semente + "-" + i, null));
        }
        List<Integer> classes = new ArrayList<>();
        int totalClasses = 1 + aleatorio.nextInt(4);
        for (int i = 0; i < totalClasses; i++) {
            classes.add(catalogo.classe("Classe " + semente + "-" + i, null));
        }
        for (Integer substancia : substancias) {
            for (Integer classe : classes) {
                if (aleatorio.nextInt(10) < 3) {
                    catalogo.substanciaClasse(substancia, classe);
                }
            }
        }
        List<Integer> produtos = new ArrayList<>();
        int totalProdutos = 2 + aleatorio.nextInt(5);
        for (int i = 0; i < totalProdutos; i++) {
            int produto = catalogo.produto("Produto " + i, marca, "limpeza_geral", null, null);
            produtos.add(produto);
            int totalFds = aleatorio.nextInt(3);
            for (int f = 0; f < totalFds; f++) {
                int fds = catalogo.fds(produto, "v" + f, LocalDate.of(2026, 1, 1 + f), aleatorio.nextInt(4) != 0, null);
                int compostos = aleatorio.nextInt(4);
                for (int c = 0; c < compostos; c++) {
                    Integer substancia = aleatorio.nextInt(6) == 0
                            ? null
                            : substancias.get(aleatorio.nextInt(substancias.size()));
                    catalogo.composto(fds, substancia, "1.00", "5.00", "%");
                }
                int declaradas = aleatorio.nextInt(3);
                for (int d = 0; d < declaradas; d++) {
                    int escolha = aleatorio.nextInt(10);
                    // 5 em 10 por substância, 3 por classe e 2 sem resolução (texto solto: não entra no match).
                    Integer substanciaDeclarada = escolha < 5 ? substancias.get(aleatorio.nextInt(substancias.size())) : null;
                    Integer classeDeclarada = escolha >= 5 && escolha < 8 ? classes.get(aleatorio.nextInt(classes.size())) : null;
                    catalogo.incompatibilidadeDaFds(
                            fds, substanciaDeclarada, classeDeclarada,
                            SEVERIDADES.get(aleatorio.nextInt(SEVERIDADES.size())),
                            aleatorio.nextInt(4) == 0 ? null : "Declarada " + produto + "/" + f + "/" + d);
                }
            }
        }
        int totalRegras = aleatorio.nextInt(12);
        for (int i = 0; i < totalRegras; i++) {
            Integer[] ladoA = lado(aleatorio, substancias, classes);
            Integer[] ladoB = lado(aleatorio, substancias, classes);
            catalogo.regra(
                    ladoA[0], ladoA[1], ladoB[0], ladoB[1],
                    SEVERIDADES.get(aleatorio.nextInt(SEVERIDADES.size())),
                    "Regra " + i,
                    aleatorio.nextInt(5) != 0);
        }
        return produtos;
    }

    private static Integer[] lado(SplittableRandom aleatorio, List<Integer> substancias, List<Integer> classes) {
        return aleatorio.nextInt(10) < 6
                ? new Integer[] {substancias.get(aleatorio.nextInt(substancias.size())), null}
                : new Integer[] {null, classes.get(aleatorio.nextInt(classes.size()))};
    }

    @Test
    void gatewayCoincideComOOraculoEmCadaParECadaConjunto() {
        Set<String> resultadosVistos = new HashSet<>();
        for (long semente = SEMENTE_INICIAL; semente < SEMENTE_INICIAL + CENARIOS; semente++) {
            catalogo.limpar();
            List<Integer> produtos = gravarCenario(semente);
            List<AvaliacaoParView> esperados = new ArrayList<>();
            for (int i = 0; i < produtos.size(); i++) {
                for (int j = i + 1; j < produtos.size(); j++) {
                    AvaliacaoParView esperado = oraculo(produtos.get(i), produtos.get(j));
                    esperados.add(esperado);
                    resultadosVistos.add(esperado.resultado());
                    if ("incompativel".equals(esperado.resultado())) {
                        resultadosVistos.add(esperado.regraId() == null ? "declarada" : "regra");
                    }
                    assertThat(gateway.avaliarPar(produtos.get(j), produtos.get(i)))
                            .as("semente %d, par %d-%d", semente, produtos.get(i), produtos.get(j))
                            .isEqualTo(esperado);
                }
            }
            AvaliacaoConjuntoView conjunto = gateway.avaliarConjunto(produtos);
            assertThat(conjunto.incompatibilidades())
                    .as("semente %d", semente)
                    .containsExactlyInAnyOrderElementsOf(
                            esperados.stream().filter(par -> "incompativel".equals(par.resultado())).toList());
            assertThat(conjunto.paresNaoAvaliados())
                    .as("semente %d", semente)
                    .isEqualTo((int) esperados.stream().filter(par -> "nao_avaliado".equals(par.resultado())).count());
        }
        assertThat(resultadosVistos)
                .containsExactlyInAnyOrder("compativel", "incompativel", "nao_avaliado", "regra", "declarada");
    }

    @Test
    void regraInativaNaoCasaEmNenhumSentido() {
        int marca = catalogo.marca("Marca", null);
        int cloro = catalogo.substancia("Cloro", "7782-50-5", null);
        int amonia = catalogo.substancia("Amônia", "7664-41-7", null);
        int a = produtoCom(marca, cloro);
        int b = produtoCom(marca, amonia);
        catalogo.regra(cloro, null, amonia, null, "critica", "Inativa A→B", false);
        catalogo.regra(amonia, null, cloro, null, "critica", "Inativa B→A", false);

        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo("compativel");
        assertThat(gateway.avaliarPar(b, a).resultado()).isEqualTo("compativel");
        assertThat(oraculo(a, b).resultado()).isEqualTo("compativel");
    }

    private int produtoCom(int marca, int substancia) {
        int produto = catalogo.produto("P" + substancia, marca, "limpeza_geral", null, null);
        catalogo.composto(catalogo.fds(produto, "1", LocalDate.of(2026, 1, 1), true, null), substancia, "1.00", "2.00", "%");
        return produto;
    }
}
