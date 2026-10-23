package com.api.quimia.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mistura registrada enquanto o produto sai da estante. Sem a trava, a mistura nova sobrevive à limpeza feita pela
 * remoção e fica no histórico de um produto que o usuário já tirou. As travas de linha só valem de verdade no
 * PostgreSQL.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class MisturaConcorrenciaTest {
    /** Tempo em que uma mistura sem trava já teria terminado; com a trava ela continua esperando. */
    private static final long ESPERA_DA_TRAVA_MS = 1500;
    private static final int RODADAS = 15;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacoes;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private TestCatalogo catalogo;
    private String token;
    private UUID usuario;
    private int marca;
    private int agua;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        assumeTrue(catalogo.postgres(), "trava de linha só no PostgreSQL");
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@mistura-concorrencia.test'");
        token = TestLogins.usuario(mvc, "a@mistura-concorrencia.test");
        usuario = catalogo.usuarioId("a@mistura-concorrencia.test");
        marca = catalogo.marca("Marca", null);
        agua = catalogo.substancia("Água", "7732-18-5", null);
    }

    @AfterEach
    void fecharPool() {
        pool.shutdownNow();
    }

    private int produtoNaEstante(String nome) {
        int produto = catalogo.produto(nome, marca, "limpeza_geral", null, null);
        catalogo.composto(catalogo.fds(produto, "1", LocalDate.of(2026, 1, 1), true, null), agua, "1.00", "2.00", "%");
        catalogo.guardar(usuario, produto);
        return produto;
    }

    private int misturar(int a, int b) throws Exception {
        return mvc.perform(post("/api/v1/misturas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoIdA\":" + a + ",\"produtoIdB\":" + b + "}"))
                .andReturn().getResponse().getStatus();
    }

    private int misturasCom(int produto) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM historico_match WHERE id_usuario = ? AND (id_produto_a = ? OR id_produto_b = ?)",
                Integer.class, usuario, produto, produto);
        return total == null ? 0 : total;
    }

    @Test
    void misturaEsperaARemocaoEmAndamentoENaoDeixaHistoricoOrfao() throws Exception {
        int alvejante = produtoNaEstante("Alvejante");
        int sabao = produtoNaEstante("Sabão");
        CountDownLatch removeu = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);

        // Mesma ordem da remoção real: apaga a linha da estante e depois as misturas do produto, na mesma transação.
        Future<?> remocao = pool.submit(() -> new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM estante_produto WHERE id_usuario = ? AND id_produto = ?", usuario, alvejante);
            jdbc.update("DELETE FROM historico_match WHERE id_usuario = ? AND (id_produto_a = ? OR id_produto_b = ?)",
                    usuario, alvejante, alvejante);
            removeu.countDown();
            try {
                assertThat(liberar.await(30, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrompido);
            }
        }));
        assertThat(removeu.await(30, TimeUnit.SECONDS)).isTrue();

        Future<Integer> mistura = pool.submit(() -> misturar(alvejante, sabao));
        assertThatThrownBy(() -> mistura.get(ESPERA_DA_TRAVA_MS, TimeUnit.MILLISECONDS))
                .as("a mistura tem de esperar a remoção terminar")
                .isInstanceOf(TimeoutException.class);
        liberar.countDown();
        remocao.get(30, TimeUnit.SECONDS);

        assertThat(mistura.get(30, TimeUnit.SECONDS)).isEqualTo(404);
        assertThat(misturasCom(alvejante)).isZero();
    }

    @Test
    void misturaERemocaoSimultaneasNuncaDeixamHistoricoDeProdutoForaDaEstante() throws Exception {
        for (int rodada = 0; rodada < RODADAS; rodada++) {
            int alvejante = produtoNaEstante("Alvejante " + rodada);
            int sabao = produtoNaEstante("Sabão " + rodada);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Integer>> tarefas = new ArrayList<>();
            tarefas.add(pool.submit(() -> {
                largada.await();
                return misturar(alvejante, sabao);
            }));
            tarefas.add(pool.submit(() -> {
                largada.await();
                return mvc.perform(delete("/api/v1/estante/produtos/" + alvejante).header("Authorization", "Bearer " + token))
                        .andReturn().getResponse().getStatus();
            }));
            largada.countDown();

            int statusDaMistura = tarefas.get(0).get(30, TimeUnit.SECONDS);
            assertThat(tarefas.get(1).get(30, TimeUnit.SECONDS)).as("rodada %d", rodada).isEqualTo(204);
            assertThat(statusDaMistura).as("rodada %d", rodada).isIn(201, 404);
            assertThat(misturasCom(alvejante)).as("rodada %d", rodada).isZero();
        }
    }
}
