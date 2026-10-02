package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.catalog.internal.dto.CadastroProdutoForm;
import com.api.quimia.domain.catalog.internal.usecase.CadastrarProdutoEmpresaUseCase;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * "Produtos cadastrados" do portal pela pilha HTTP completa. Roda em H2 no build e no
 * PostgreSQL com o DDL real do quimia-bd via scripts/local-validation/run-pg-tests.sh.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class EmpresaProdutoWebTest {
    private static final String BASE = "/api/v1/empresas/me/produtos";
    private static final int CADASTROS_SIMULTANEOS = 8;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CadastrarProdutoEmpresaUseCase cadastrarProduto;

    private TestCatalogo catalogo;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@portal.test'");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@portal.test'");
        tokenA = TestLogins.empresa(mvc, "Omo Brasil", "a@portal.test", "22.334.455/0001-86");
        tokenB = TestLogins.empresa(mvc, "Veja Ltda", "b@portal.test", "33.445.566/0001-86");
    }

    @Test
    void cadastraUsandoAMarcaDaEmpresaUmaUnicaVez() throws Exception {
        cadastrar(tokenA, "Limpa Vidros", "limpeza_geral", "7894000000001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("criado"))
                .andExpect(jsonPath("$.produto.nome").value("Limpa Vidros"))
                .andExpect(jsonPath("$.produto.tipo").value("limpeza_geral"))
                .andExpect(jsonPath("$.produto.descricao").value("Descrição de Limpa Vidros"))
                .andExpect(jsonPath("$.produto.marca.nome").value("Omo Brasil"));
        cadastrar(tokenA, "Tira Limo", "desinfetante", "7894000000002").andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM marca WHERE nome = 'Omo Brasil'", Integer.class))
                .isEqualTo(1);
        listar(tokenA, "")
                .andExpect(jsonPath("$.totalItens").value(2))
                .andExpect(jsonPath("$.itens[*].nome", Matchers.contains("Limpa Vidros", "Tira Limo")));
    }

    @Test
    void reaproveitaMarcaExistenteSemDiferenciarMaiusculas() throws Exception {
        catalogo.marca("OMO BRASIL", "https://cdn.test/omo.png");

        cadastrar(tokenA, "Limpa Vidros", "limpeza_geral", "7894000000003")
                .andExpect(jsonPath("$.produto.marca.nome").value("OMO BRASIL"))
                .andExpect(jsonPath("$.produto.marca.logoUrl").value("https://cdn.test/omo.png"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marca WHERE lower(nome) = 'omo brasil'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void codigoDeBarrasExistenteVinculaSemDuplicar() throws Exception {
        int id = idDe(cadastrar(tokenA, "Limpa Vidros", "limpeza_geral", "7894000000004"));

        cadastrar(tokenB, "Nome Diferente", "outro", "7894000000004")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("vinculado"))
                .andExpect(jsonPath("$.produto.id").value(id))
                .andExpect(jsonPath("$.produto.nome").value("Limpa Vidros"));
        cadastrar(tokenB, "De Novo", "outro", "7894000000004")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("produto_ja_cadastrado"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM produto WHERE cod_barras = '7894000000004'", Integer.class))
                .isEqualTo(1);
        listar(tokenB, "").andExpect(jsonPath("$.itens[*].id", Matchers.contains(id)));
    }

    @Test
    void deletarSoDesativaOVinculoEMantemAEstanteDoUsuario() throws Exception {
        int id = idDe(cadastrar(tokenA, "Limpa Vidros", "limpeza_geral", "7894000000005"));
        String usuario = TestLogins.usuario(mvc, "usuario@portal.test");
        mvc.perform(post("/api/v1/estante/produtos").header("Authorization", "Bearer " + usuario)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIds\":[" + id + "]}"))
                .andExpect(jsonPath("$.itens[0].status").value("adicionado"));

        remover(tokenA, id).andExpect(status().isNoContent());
        remover(tokenA, id).andExpect(status().isNoContent());

        listar(tokenA, "").andExpect(jsonPath("$.totalItens").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM produto WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ativo FROM empresa_produto WHERE id_produto = ?", Boolean.class, id))
                .isFalse();
        mvc.perform(get("/api/v1/estante/produtos").header("Authorization", "Bearer " + usuario))
                .andExpect(jsonPath("$.itens[*].id", Matchers.contains(id)));
        mvc.perform(get("/api/v1/produtos/" + id).header("Authorization", "Bearer " + usuario))
                .andExpect(status().isNotFound());

        cadastrar(tokenA, "Limpa Vidros", "limpeza_geral", "7894000000005")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("reativado"));
        mvc.perform(get("/api/v1/produtos/" + id).header("Authorization", "Bearer " + usuario))
                .andExpect(status().isOk());
    }

    @Test
    void alteraSoProdutoDaEmpresaQueNaoEstejaCompartilhado() throws Exception {
        int unico = idDe(cadastrar(tokenA, "Limpa Vidros", "limpeza_geral", "7894000000006"));
        int outro = idDe(cadastrar(tokenA, "Tira Limo", "desinfetante", "7894000000007"));

        alterar(tokenA, unico, "nome", "Limpa Vidros Plus", "tipo", "desengraxante")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Limpa Vidros Plus"))
                .andExpect(jsonPath("$.tipo").value("desengraxante"))
                .andExpect(jsonPath("$.codBarras").value("7894000000006"));
        alterar(tokenA, unico, "codBarras", "7894000000007")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("cod_barras_em_uso"));
        alterar(tokenB, unico, "nome", "Invasão").andExpect(status().isNotFound());

        cadastrar(tokenB, "Tira Limo", "desinfetante", "7894000000007").andExpect(jsonPath("$.situacao").value("vinculado"));
        alterar(tokenA, outro, "nome", "Mudado")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("produto_compartilhado"));
    }

    @Test
    void validaCamposEFiltros() throws Exception {
        mvc.perform(multipart(BASE).param("tipo", "outro").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
        cadastrar(tokenA, "Produto", "squeeze", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("tipo_invalido"));
        cadastrar(tokenA, "Produto", "outro", "12ab")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("codigo_barras_invalido"));
        cadastrar(tokenA, "Sem Codigo", "outro", null).andExpect(status().isCreated());
        listar(tokenA, "tipo=outro").andExpect(jsonPath("$.itens[*].nome", Matchers.contains("Sem Codigo")));
        listar(tokenA, "q=codigo").andExpect(jsonPath("$.totalItens").value(1));
        listar(tokenA, "size=0").andExpect(status().isBadRequest());
    }

    @Test
    void exigeEmpresaAtivaEIsolaAsEmpresas() throws Exception {
        int id = idDe(cadastrar(tokenA, "Limpa Vidros", "limpeza_geral", "7894000000008"));

        listar(tokenB, "").andExpect(jsonPath("$.totalItens").value(0));
        remover(tokenB, id).andExpect(status().isNotFound());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).header("Authorization", "Bearer " + TestLogins.usuario(mvc, "app@portal.test")))
                .andExpect(status().isForbidden());

        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE email = 'a@portal.test'");
        listar(tokenA, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("empresa_inativa"));
    }

    @Test
    void cadastrosSimultaneosCriamUmaUnicaMarca() throws Exception {
        Integer empresaId = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'a@portal.test'", Integer.class);
        ExecutorService pool = Executors.newFixedThreadPool(CADASTROS_SIMULTANEOS);
        List<Callable<Object>> chamadas = new ArrayList<>();
        for (int i = 0; i < CADASTROS_SIMULTANEOS; i++) {
            CadastroProdutoForm form = new CadastroProdutoForm("Produto " + i, "outro", null, "78950000000" + i);
            chamadas.add(() -> cadastrarProduto.execute(empresaId, form));
        }
        try {
            for (Future<Object> futuro : pool.invokeAll(chamadas)) {
                futuro.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM marca WHERE nome = 'Omo Brasil'", Integer.class))
                .isEqualTo(1);
        listar(tokenA, "").andExpect(jsonPath("$.totalItens").value(CADASTROS_SIMULTANEOS));
    }

    private ResultActions cadastrar(String token, String nome, String tipo, String codBarras) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(BASE)
                .param("nome", nome)
                .param("tipo", tipo)
                .param("descricao", "Descrição de " + nome);
        if (codBarras != null) {
            request.param("codBarras", codBarras);
        }
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions alterar(String token, int id, String... campos) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PATCH, BASE + "/" + id);
        for (int i = 0; i < campos.length; i += 2) {
            request.param(campos[i], campos[i + 1]);
        }
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions listar(String token, String query) throws Exception {
        return mvc.perform(get(BASE + "?" + query).header("Authorization", "Bearer " + token));
    }

    private ResultActions remover(String token, int id) throws Exception {
        return mvc.perform(delete(BASE + "/" + id).header("Authorization", "Bearer " + token));
    }

    private static int idDe(ResultActions cadastro) throws Exception {
        return JsonPath.read(cadastro.andReturn().getResponse().getContentAsString(), "$.produto.id");
    }
}
