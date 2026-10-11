package com.api.quimia.domain.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class OrdenacaoEmpresaWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private int a;
    private int b;
    private int c;

    @BeforeEach
    void seed() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@ordenacao.test'");
        token = TestLogins.empresa(mvc, "Empresa Ord", "e@ordenacao.test", "30000008000119");
        int empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@ordenacao.test'", Integer.class);
        int marca = catalogo.marca("Marca", null);
        a = catalogo.produto("Beta", marca, "desinfetante", "300", "zeta descrição");
        b = catalogo.produto("Alfa", marca, "alvejante", "100", null);
        c = catalogo.produto("Gama", marca, "limpeza_geral", null, "alfa descrição");
        for (int produto : new int[] {a, b, c}) {
            catalogo.vinculo(empresa, produto, "TRUE");
        }
    }

    private ResultActions listar(String... parametros) throws Exception {
        var pedido = get("/api/v1/empresas/me/produtos").header("Authorization", "Bearer " + token);
        for (int i = 0; i < parametros.length; i += 2) {
            pedido.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(pedido);
    }

    @Test
    void padraoEPorNomeCrescente() throws Exception {
        listar().andExpect(jsonPath("$.itens[0].id").value(b)).andExpect(jsonPath("$.itens[1].id").value(a))
                .andExpect(jsonPath("$.itens[2].id").value(c));
    }

    @Test
    void nomeDescendente() throws Exception {
        listar("sort", "nome", "direcao", "desc")
                .andExpect(jsonPath("$.itens[0].id").value(c))
                .andExpect(jsonPath("$.itens[2].id").value(b));
    }

    @Test
    void tipoCrescente() throws Exception {
        // alvejante < desinfetante < limpeza_geral
        listar("sort", "tipo").andExpect(jsonPath("$.itens[0].id").value(b))
                .andExpect(jsonPath("$.itens[1].id").value(a)).andExpect(jsonPath("$.itens[2].id").value(c));
    }

    @Test
    void descricaoComNuloPorUltimoNosDoisSentidos() throws Exception {
        listar("sort", "descricao", "direcao", "asc")
                .andExpect(jsonPath("$.itens[0].id").value(c))
                .andExpect(jsonPath("$.itens[1].id").value(a))
                .andExpect(jsonPath("$.itens[2].id").value(b));
        listar("sort", "descricao", "direcao", "desc")
                .andExpect(jsonPath("$.itens[0].id").value(a))
                .andExpect(jsonPath("$.itens[1].id").value(c))
                .andExpect(jsonPath("$.itens[2].id").value(b));
    }

    @Test
    void codBarrasComNuloPorUltimo() throws Exception {
        listar("sort", "codBarras").andExpect(jsonPath("$.itens[0].id").value(b))
                .andExpect(jsonPath("$.itens[1].id").value(a)).andExpect(jsonPath("$.itens[2].id").value(c));
    }

    @Test
    void empateDesempataPorIdEAPaginacaoNaoRepete() throws Exception {
        int marca = jdbc.queryForObject("SELECT id FROM marca LIMIT 1", Integer.class);
        int empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@ordenacao.test'", Integer.class);
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        int d = catalogo.produto("Delta", marca, "alvejante", "400", null);
        catalogo.vinculo(empresa, d, "TRUE");

        listar("sort", "tipo", "size", "2", "page", "0")
                .andExpect(jsonPath("$.itens[0].id").value(b)) // alvejante, id menor
                .andExpect(jsonPath("$.itens[1].id").value(d)); // alvejante, id maior
        listar("sort", "tipo", "size", "2", "page", "1")
                .andExpect(jsonPath("$.itens[0].id").value(a))
                .andExpect(jsonPath("$.itens[1].id").value(c));
    }

    @Test
    void valoresInvalidosDao400() throws Exception {
        for (String sort : new String[] {"senha", "", "NOME", "id", "marca.nome"}) {
            listar("sort", sort).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("ordenacao_invalida"));
        }
        for (String direcao : new String[] {"baixo", "", "ASC"}) {
            listar("direcao", direcao).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("ordenacao_invalida"));
        }
    }
}
