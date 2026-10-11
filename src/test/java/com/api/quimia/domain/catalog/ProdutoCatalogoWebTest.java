package com.api.quimia.domain.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Catálogo do app pela pilha HTTP completa. Roda em H2 no build e no PostgreSQL com o DDL real
 * do quimia-bd via scripts/local-validation/run-pg-tests.sh.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class ProdutoCatalogoWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String usuarioToken;
    private int limpaVidros;
    private int aguaSanitaria;
    private int desinfetante;
    private int oculto;
    private int compartilhado;

    @BeforeEach
    void seed() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@catalogo.test'");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@catalogo.test'");
        usuarioToken = TestLogins.usuario(mvc, "app@catalogo.test");

        int mrMusculo = catalogo.marca("Mr Músculo", "https://cdn.test/mr.png");
        int veja = catalogo.marca("Veja", null);
        int empresaA = catalogo.empresa("A", "a@catalogo.test");
        int empresaB = catalogo.empresa("B", "b@catalogo.test");

        limpaVidros = catalogo.produto("Limpa Vidros", mrMusculo, "limpeza_geral", "7891000000001", "Remove gordura e marcas");
        aguaSanitaria = catalogo.produto("Água Sanitária", veja, "alvejante", "789100000002", "Alvejante clorado");
        desinfetante = catalogo.produto("Desinfetante Pinho", veja, "desinfetante", "0789100000003", "Perfume de pinho");
        oculto = catalogo.produto("Limpador Oculto", veja, "limpeza_geral", "7891000000004", "Fora de linha");
        compartilhado = catalogo.produto("Multiuso Compartilhado", mrMusculo, "limpeza_geral", "7891000000005", "100% multiuso");

        catalogo.vinculo(empresaA, aguaSanitaria, "TRUE");
        catalogo.vinculo(empresaA, desinfetante, "NULL");
        catalogo.vinculo(empresaA, oculto, "FALSE");
        catalogo.vinculo(empresaA, compartilhado, "FALSE");
        catalogo.vinculo(empresaB, compartilhado, "TRUE");
    }

    @Test
    void listaSoProdutosVisiveis() throws Exception {
        listar("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(4))
                .andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(
                        limpaVidros, aguaSanitaria, desinfetante, compartilhado)))
                .andExpect(jsonPath("$.itens[*].nome", Matchers.not(Matchers.hasItem("Limpador Oculto"))));
    }

    @Test
    void buscaIgnoraMaiusculasEAcentosNoNomeENaDescricao() throws Exception {
        listar("q=agua sanitaria").andExpect(jsonPath("$.itens[*].id", Matchers.contains(aguaSanitaria)));
        listar("q=LIMPA vid").andExpect(jsonPath("$.itens[*].id", Matchers.contains(limpaVidros)));
        listar("q=gordura").andExpect(jsonPath("$.itens[*].id", Matchers.contains(limpaVidros)));
        listar("q=oculto").andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void curingasDoTermoSaoLiterais() throws Exception {
        buscar("%").andExpect(jsonPath("$.itens[*].id", Matchers.contains(compartilhado)));
        buscar("_").andExpect(jsonPath("$.totalItens").value(0));
        buscar("!").andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void filtraPorTipoECodigoOrdenandoPorNome() throws Exception {
        listar("tipo=limpeza_geral").andExpect(jsonPath("$.itens[*].nome", Matchers.contains(
                "Limpa Vidros", "Multiuso Compartilhado")));
        listar("codBarras=789100000002").andExpect(jsonPath("$.itens[*].id", Matchers.contains(aguaSanitaria)));
        listar("tipo=squeeze")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("tipo_invalido"));
    }

    @Test
    void paginaOsResultados() throws Exception {
        listar("size=3&page=1")
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamanho").value(3))
                .andExpect(jsonPath("$.totalItens").value(4))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        listar("size=0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("paginacao_invalida"));
        listar("size=51").andExpect(status().isBadRequest());
        listar("page=-1").andExpect(status().isBadRequest());
        listar("page=2147483647&size=50")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("paginacao_invalida"));
    }

    @Test
    void buscaTambemPeloNomeDaMarca() throws Exception {
        buscar("veja").andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(aguaSanitaria, desinfetante)));
        buscar("mr musculo").andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(limpaVidros, compartilhado)));
    }

    @Test
    void entradaQueNaoConverteRespondeProblemDetail() throws Exception {
        listar("size=abc")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("parametro_invalido"));
        mvc.perform(get("/api/v1/produtos/abc").header("Authorization", "Bearer " + usuarioToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("parametro_invalido"));
        mvc.perform(post("/api/v1/produtos/busca-por-codigo-barras")
                        .header("Authorization", "Bearer " + usuarioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codBarras\":{\"x\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("corpo_invalido"));
        mvc.perform(post("/api/v1/produtos/busca-por-codigo-barras")
                        .header("Authorization", "Bearer " + usuarioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codBarras\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("corpo_invalido"));
        mvc.perform(post("/api/v1/produtos/busca-por-codigo-barras")
                        .header("Authorization", "Bearer " + usuarioToken)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("7891000000001"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("tipo_midia_nao_suportado"));
    }

    @Test
    void detalheTrazMarcaEEscondeProdutoOculto() throws Exception {
        mvc.perform(get("/api/v1/produtos/" + limpaVidros).header("Authorization", "Bearer " + usuarioToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Limpa Vidros"))
                .andExpect(jsonPath("$.tipo").value("limpeza_geral"))
                .andExpect(jsonPath("$.codBarras").value("7891000000001"))
                .andExpect(jsonPath("$.marca.nome").value("Mr Músculo"))
                .andExpect(jsonPath("$.marca.logoUrl").value("https://cdn.test/mr.png"));
        mvc.perform(get("/api/v1/produtos/" + oculto).header("Authorization", "Bearer " + usuarioToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_encontrado"));
        mvc.perform(get("/api/v1/produtos/999999").header("Authorization", "Bearer " + usuarioToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void buscaPorCodigoDeBarrasAceitaUpcEEan() throws Exception {
        codigo("7891000000001").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(limpaVidros));
        codigo("0789100000002").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(aguaSanitaria));
        codigo("789100000003").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(desinfetante));
        codigo(" 7891000000005 ").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(compartilhado));
        codigo("7891000000004").andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("produto_nao_encontrado"));
        codigo("1111111111111").andExpect(status().isNotFound());
        codigo("78910abc").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail").value("codigo_barras_invalido"));
        codigo("").andExpect(status().isBadRequest());
    }

    @Test
    void exigeTokenDeUsuario() throws Exception {
        mvc.perform(get("/api/v1/produtos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/produtos").header("Authorization", "Bearer " + TestLogins.empresa(mvc, "portal@catalogo.test", "11.444.777/0001-61")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/produtos/busca-por-codigo-barras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codBarras\":\"7891000000001\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions listar(String query) throws Exception {
        return mvc.perform(get("/api/v1/produtos?" + query).header("Authorization", "Bearer " + usuarioToken));
    }

    /** Termo enviado como parâmetro já codificado pelo MockMvc (sem montar a URL à mão). */
    private ResultActions buscar(String termo) throws Exception {
        return mvc.perform(get("/api/v1/produtos").param("q", termo).header("Authorization", "Bearer " + usuarioToken));
    }

    private ResultActions codigo(String codBarras) throws Exception {
        return mvc.perform(post("/api/v1/produtos/busca-por-codigo-barras")
                .header("Authorization", "Bearer " + usuarioToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codBarras\":\"" + codBarras + "\"}"));
    }
}
