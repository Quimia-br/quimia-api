package com.api.quimia.domain.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.storage.event.ProdutoRemovidoDaEstanteEvent;
import com.api.quimia.domain.storage.internal.dto.ItemGuardado;
import com.api.quimia.domain.storage.internal.usecase.GuardarNaEstanteUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Estante pela pilha HTTP completa. Roda em H2 no build e no PostgreSQL com o DDL real do
 * quimia-bd (UNIQUE e trigger de dono da estante) via scripts/local-validation/run-pg-tests.sh.
 */
@RecordApplicationEvents
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class EstanteWebTest {
    private static final int GUARDAR_SIMULTANEOS = 10;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GuardarNaEstanteUseCase guardarNaEstante;

    @Autowired
    private ApplicationEvents events;

    private String tokenA;
    private String tokenB;
    private int limpaVidros;
    private int aguaSanitaria;
    private int oculto;
    private int empresa;

    @BeforeEach
    void seed() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@estante.test'");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@estante.test'");
        tokenA = TestLogins.usuario(mvc, "a@estante.test");
        tokenB = TestLogins.usuario(mvc, "b@estante.test");

        int marca = catalogo.marca("Mr Músculo", null);
        empresa = catalogo.empresa("Empresa", "empresa@estante.test");
        limpaVidros = catalogo.produto("Limpa Vidros", marca, "limpeza_geral", "7893000000001", null);
        aguaSanitaria = catalogo.produto("Água Sanitária", marca, "alvejante", "7893000000002", null);
        oculto = catalogo.produto("Oculto", marca, "outro", "7893000000003", null);
        catalogo.vinculo(empresa, aguaSanitaria, "TRUE");
        catalogo.vinculo(empresa, oculto, "FALSE");
    }

    @Test
    void guardarCriaEstanteUnicaEInformaCadaProduto() throws Exception {
        guardar(tokenA, limpaVidros, aguaSanitaria, oculto, 999999, limpaVidros)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(4))
                .andExpect(jsonPath("$.itens[0].produtoId").value(limpaVidros))
                .andExpect(jsonPath("$.itens[0].nome").value("Limpa Vidros"))
                .andExpect(jsonPath("$.itens[0].status").value("adicionado"))
                .andExpect(jsonPath("$.itens[1].status").value("adicionado"))
                .andExpect(jsonPath("$.itens[2].status").value("nao_encontrado"))
                .andExpect(jsonPath("$.itens[2].nome").doesNotExist())
                .andExpect(jsonPath("$.itens[3].produtoId").value(999999))
                .andExpect(jsonPath("$.itens[3].status").value("nao_encontrado"));

        UUID usuario = usuarioId("a@estante.test");
        assertThat(jdbc.queryForList("SELECT nome FROM estante WHERE id_usuario = ?", String.class, usuario))
                .containsExactly("Minha estante");
        assertThat(contarItens(usuario)).isEqualTo(2);
    }

    @Test
    void guardarDeNovoNaoDuplica() throws Exception {
        guardar(tokenA, limpaVidros).andExpect(jsonPath("$.itens[0].status").value("adicionado"));
        guardar(tokenA, limpaVidros).andExpect(jsonPath("$.itens[0].status").value("ja_na_estante"));

        assertThat(contarItens(usuarioId("a@estante.test"))).isEqualTo(1);
    }

    @Test
    void listaComBuscaEPaginacaoMantendoProdutoQueFicouOculto() throws Exception {
        guardar(tokenA, limpaVidros, aguaSanitaria);
        jdbc.update("UPDATE empresa_produto SET ativo = FALSE WHERE id_produto = ?", aguaSanitaria);

        listar(tokenA, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(2))
                .andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(limpaVidros, aguaSanitaria)));
        listar(tokenA, "q=agua").andExpect(jsonPath("$.itens[*].id", Matchers.contains(aguaSanitaria)));
        listar(tokenA, "size=1&page=1")
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        listar(tokenA, "size=0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("paginacao_invalida"));
        listar(tokenA, "page=2147483647&size=50")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("paginacao_invalida"));
        guardar(tokenA, aguaSanitaria).andExpect(jsonPath("$.itens[0].status").value("nao_encontrado"));
    }

    @Test
    void estanteDeUmUsuarioNaoApareceNemMudaParaOutro() throws Exception {
        guardar(tokenA, limpaVidros);

        listar(tokenB, "").andExpect(jsonPath("$.totalItens").value(0));
        mvc.perform(delete("/api/v1/estante/produtos/" + limpaVidros).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());
        listar(tokenA, "").andExpect(jsonPath("$.itens[*].id", Matchers.contains(limpaVidros)));
    }

    @Test
    void removerEhIdempotenteEPublicaEventoUmaVez() throws Exception {
        guardar(tokenA, limpaVidros);

        remover(tokenA, limpaVidros).andExpect(status().isNoContent());
        remover(tokenA, limpaVidros).andExpect(status().isNoContent());

        listar(tokenA, "").andExpect(jsonPath("$.totalItens").value(0));
        assertThat(events.stream(ProdutoRemovidoDaEstanteEvent.class))
                .containsExactly(new ProdutoRemovidoDaEstanteEvent(usuarioId("a@estante.test"), limpaVidros));
    }

    @Test
    void validaPedidoEExigeTokenDeUsuario() throws Exception {
        mvc.perform(post("/api/v1/estante/produtos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        guardar(tokenA).andExpect(status().isBadRequest());
        guardar(tokenA, IntStream.rangeClosed(1, 51).toArray()).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/estante/produtos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIds\":[" + limpaVidros + ".9]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("corpo_invalido"));
        assertThat(contarItens(usuarioId("a@estante.test"))).isZero();
        mvc.perform(post("/api/v1/estante/produtos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIds\":[\"" + limpaVidros + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("corpo_invalido"));
        assertThat(contarItens(usuarioId("a@estante.test"))).isZero();
        mvc.perform(get("/api/v1/estante/produtos")).andExpect(status().isUnauthorized());
        String tokenEmpresa = TestLogins.empresa(mvc, "portal@estante.test", "44.556.677/0001-86");
        mvc.perform(get("/api/v1/estante/produtos").header("Authorization", "Bearer " + tokenEmpresa))
                .andExpect(status().isForbidden());
    }

    @Test
    void guardarSimultaneoCriaUmaEstanteEUmaLinhaPorProduto() throws Exception {
        UUID usuario = usuarioId("a@estante.test");
        ExecutorService pool = Executors.newFixedThreadPool(GUARDAR_SIMULTANEOS);
        List<Callable<List<ItemGuardado>>> chamadas = new ArrayList<>();
        for (int i = 0; i < GUARDAR_SIMULTANEOS; i++) {
            chamadas.add(() -> guardarNaEstante.execute(usuario, List.of(limpaVidros, aguaSanitaria)));
        }
        List<ItemGuardado> resultados = new ArrayList<>();
        try {
            for (Future<List<ItemGuardado>> futuro : pool.invokeAll(chamadas)) {
                resultados.addAll(futuro.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM estante WHERE id_usuario = ?", Integer.class, usuario))
                .isEqualTo(1);
        assertThat(contarItens(usuario)).isEqualTo(2);
        assertThat(resultados.stream()
                        .filter(item -> item.status().equals("adicionado"))
                        .collect(Collectors.groupingBy(ItemGuardado::produtoId, Collectors.counting())))
                .containsEntry(limpaVidros, 1L)
                .containsEntry(aguaSanitaria, 1L);
    }

    private ResultActions guardar(String token, int... produtoIds) throws Exception {
        String ids = IntStream.of(produtoIds).mapToObj(String::valueOf).collect(Collectors.joining(","));
        return mvc.perform(post("/api/v1/estante/produtos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoIds\":[" + ids + "]}"));
    }

    private ResultActions listar(String token, String query) throws Exception {
        return mvc.perform(get("/api/v1/estante/produtos?" + query).header("Authorization", "Bearer " + token));
    }

    private ResultActions remover(String token, int produtoId) throws Exception {
        return mvc.perform(delete("/api/v1/estante/produtos/" + produtoId).header("Authorization", "Bearer " + token));
    }

    private UUID usuarioId(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private int contarItens(UUID usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM estante_produto WHERE id_usuario = ?", Integer.class, usuario);
    }
}
