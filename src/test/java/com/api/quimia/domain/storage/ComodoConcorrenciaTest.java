package com.api.quimia.domain.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** O advisory lock e o UPDATE condicional só existem de verdade no PostgreSQL. */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class ComodoConcorrenciaTest {
    private static final int SIMULTANEOS = 8;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private int[] criarProdutos(TestCatalogo catalogo, UUID usuario, int quantidade) {
        int marca = catalogo.marca("M" + System.nanoTime(), null);
        int[] ids = new int[quantidade];
        for (int i = 0; i < quantidade; i++) {
            ids[i] = catalogo.produto("P" + i + "-" + System.nanoTime(), marca, "outro", null, null);
            catalogo.guardar(usuario, ids[i]);
        }
        return ids;
    }

    private List<Integer> statusSimultaneos(List<Callable<Integer>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (Callable<Integer> tarefa : tarefas) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return tarefa.call();
            }));
        }
        largada.countDown();
        List<Integer> status = new ArrayList<>();
        for (Future<Integer> futuro : futuros) {
            status.add(futuro.get());
        }
        pool.shutdown();
        return status;
    }

    private int postar(String token, String nome, int... produtos) throws Exception {
        String ids = Arrays.stream(produtos).mapToObj(String::valueOf).collect(Collectors.joining(","));
        return mvc.perform(post("/api/v1/comodos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"icone\":\"toilet\",\"produtoIds\":[" + ids + "]}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void mesmoNomeSimultaneoCriaUmSoComodo() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        assumeTrue(catalogo.postgres(), "advisory lock só no PostgreSQL");
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@concorrencia.test'");
        String token = TestLogins.usuario(mvc, "a@concorrencia.test");
        UUID usuario = catalogo.usuarioId("a@concorrencia.test");
        int[] produtos = criarProdutos(catalogo, usuario, SIMULTANEOS * 2);
        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (int i = 0; i < SIMULTANEOS; i++) {
            int a = produtos[i * 2];
            int b = produtos[i * 2 + 1];
            tarefas.add(() -> postar(token, "Banheiro", a, b));
        }

        List<Integer> status = statusSimultaneos(tarefas);

        assertThat(status.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(status.stream().filter(s -> s == 409)).hasSize(SIMULTANEOS - 1);
        Integer comodos = jdbc.queryForObject("SELECT count(*) FROM comodo WHERE id_usuario = ?", Integer.class, usuario);
        assertThat(comodos).isEqualTo(1);
    }

    @Test
    void mesmoProdutoEmDoisComodosSimultaneosFicaEmUmSo() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        assumeTrue(catalogo.postgres(), "UPDATE condicional concorrente só no PostgreSQL");
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@concorrencia.test'");
        String token = TestLogins.usuario(mvc, "b@concorrencia.test");
        UUID usuario = catalogo.usuarioId("b@concorrencia.test");
        int[] p = criarProdutos(catalogo, usuario, 3);

        List<Integer> status = statusSimultaneos(List.of(
                () -> postar(token, "Quarto", p[0], p[1]),
                () -> postar(token, "Sala", p[1], p[2])));

        assertThat(status).containsExactlyInAnyOrder(201, 409);
        Integer comodos = jdbc.queryForObject("SELECT count(*) FROM comodo WHERE id_usuario = ?", Integer.class, usuario);
        Integer vinculados = jdbc.queryForObject(
                "SELECT count(*) FROM estante_produto WHERE id_usuario = ? AND id_comodo IS NOT NULL", Integer.class, usuario);
        assertThat(comodos).isEqualTo(1);
        assertThat(vinculados).isEqualTo(2);
    }
}
