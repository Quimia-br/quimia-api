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
class CodigoBarrasEquivalenteWebTest {
    private static final String UPC_A = "789200000002";
    private static final String EAN_13 = "0789200000002";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenUsuario;
    private String tokenEmpresa;
    private int upc;
    private int ean;

    @BeforeEach
    void seed() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@codigo-eq.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@codigo-eq.test'");
        tokenUsuario = TestLogins.usuario(mvc, "u@codigo-eq.test");
        tokenEmpresa = TestLogins.empresa(mvc, "Empresa Eq", "e@codigo-eq.test", "30000007000174");
        int empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@codigo-eq.test'", Integer.class);
        int marca = catalogo.marca("Marca", null);
        upc = catalogo.produto("Gravado como UPC-A", marca, "limpeza_geral", UPC_A, null);
        ean = catalogo.produto("Gravado como EAN-13", marca, "limpeza_geral", "7891000100103", null);
        catalogo.vinculo(empresa, upc, "TRUE");
        catalogo.vinculo(empresa, ean, "TRUE");
    }

    private ResultActions app(String codigo) throws Exception {
        return mvc.perform(get("/api/v1/produtos").param("codBarras", codigo).header("Authorization", "Bearer " + tokenUsuario));
    }

    private ResultActions portal(String codigo) throws Exception {
        return mvc.perform(get("/api/v1/empresas/me/produtos").param("codBarras", codigo)
                .header("Authorization", "Bearer " + tokenEmpresa));
    }

    @Test
    void ean13ComZeroAchaOProdutoGravadoComoUpcNoApp() throws Exception {
        app(EAN_13).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(upc));
    }

    @Test
    void upcAchaOProdutoGravadoComoEanComZeroNoApp() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        int marca = catalogo.marca("M", null);
        int gravadoComZero = catalogo.produto("Com zero", marca, "limpeza_geral", EAN_13, null);

        app(UPC_A).andExpect(jsonPath("$.totalItens").value(1)).andExpect(jsonPath("$.itens[0].id").value(gravadoComZero));
    }

    @Test
    void codigoExatoContinuaFuncionando() throws Exception {
        app("7891000100103").andExpect(jsonPath("$.itens[0].id").value(ean));
        app(UPC_A).andExpect(jsonPath("$.itens[0].id").value(upc));
    }

    /** Mesma regra do escaneamento: só o espaço sai das pontas; TAB e NUL não são removidos em silêncio. */
    @Test
    void filtroDoAppSoIgnoraEspacoNasPontas() throws Exception {
        app("  " + UPC_A + "  ").andExpect(jsonPath("$.totalItens").value(1)).andExpect(jsonPath("$.itens[0].id").value(upc));
        app("\t" + UPC_A).andExpect(status().isOk()).andExpect(jsonPath("$.totalItens").value(0));
        app(UPC_A + "\u0000").andExpect(status().isOk()).andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void codigoDeOnzeDigitosNaoGanhaEquivalente() throws Exception {
        app("78920000000").andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void ean13QueNaoComecaComZeroNaoGanhaEquivalente() throws Exception {
        app("7892000000025").andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void semCodigoOuEmBrancoNaoFiltra() throws Exception {
        mvc.perform(get("/api/v1/produtos").header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(jsonPath("$.totalItens").value(2));
        app("  ").andExpect(jsonPath("$.totalItens").value(2));
    }

    @Test
    void combinaComTipoETermo() throws Exception {
        mvc.perform(get("/api/v1/produtos").param("codBarras", EAN_13).param("tipo", "alvejante")
                        .header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(jsonPath("$.totalItens").value(0));
        mvc.perform(get("/api/v1/produtos").param("codBarras", EAN_13).param("q", "upc")
                        .header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void portalAceitaAsDuasFormas() throws Exception {
        portal(EAN_13).andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].id").value(upc));
        portal(UPC_A).andExpect(jsonPath("$.itens[0].id").value(upc));
    }

    @Test
    void portalAindaRecusaCodigoComNaoDigitos() throws Exception {
        portal("abc").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("codigo_barras_invalido"));
    }

    @Test
    void produtoOcultoNaoApareceNoAppNemSemVinculoAtivoNoPortal() throws Exception {
        int empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@codigo-eq.test'", Integer.class);
        jdbc.update("UPDATE empresa_produto SET ativo = FALSE WHERE id_empresa = ? AND id_produto = ?", empresa, upc);

        app(UPC_A).andExpect(jsonPath("$.totalItens").value(0));
        portal(UPC_A).andExpect(jsonPath("$.totalItens").value(0));
    }
}
