package com.api.quimia.domain.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(InsightsDoubles.class)
class InsightsWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestCatalogo catalogo;
    private String tokenEmpresa;
    private String tokenOutraEmpresa;
    private int empresa;
    private int outraEmpresa;
    private UUID usuarioA;
    private UUID usuarioB;
    private int p1;
    private int p2;
    private int deOutra;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@insights.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@insights.test'");
        tokenEmpresa = TestLogins.empresa(mvc, "Empresa Um", "um@insights.test", "30000005000185");
        tokenOutraEmpresa = TestLogins.empresa(mvc, "Empresa Dois", "dois@insights.test", "30000006000120");
        empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'um@insights.test'", Integer.class);
        outraEmpresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'dois@insights.test'", Integer.class);
        TestLogins.usuario(mvc, "a@insights.test");
        TestLogins.usuario(mvc, "b@insights.test");
        usuarioA = catalogo.usuarioId("a@insights.test");
        usuarioB = catalogo.usuarioId("b@insights.test");
        int marca = catalogo.marca("Marca", null);
        p1 = catalogo.produto("Produto 1", marca, "limpeza_geral", null, null);
        p2 = catalogo.produto("Produto 2", marca, "desinfetante", null, null);
        deOutra = catalogo.produto("Da outra empresa", marca, "outro", null, null);
        catalogo.vinculo(empresa, p1, "TRUE");
        catalogo.vinculo(empresa, p2, "NULL");
        catalogo.vinculo(outraEmpresa, deOutra, "TRUE");
    }

    private ResultActions resumo(String token) throws Exception {
        return mvc.perform(get("/api/v1/empresas/me/resumo").header("Authorization", "Bearer " + token));
    }

    private ResultActions estatisticas(String token, String... q) throws Exception {
        var pedido = get("/api/v1/empresas/me/estatisticas").header("Authorization", "Bearer " + token);
        if (q.length > 0) {
            pedido.param("q", q[0]);
        }
        return mvc.perform(pedido);
    }

    // --- GET /empresas/me/resumo ---

    @Test
    void empresaSemProdutosVeZeros() throws Exception {
        jdbc.update("DELETE FROM empresa_produto WHERE id_empresa = ?", empresa);

        resumo(tokenEmpresa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtosEmEstantes").value(0))
                .andExpect(jsonPath("$.misturas.total").value(0))
                .andExpect(jsonPath("$.misturas.taxaSucesso").doesNotExist())
                .andExpect(jsonPath("$.produtoDestaque").doesNotExist());
    }

    @Test
    void contaGuardadosDeUsuariosDistintosEIgnoraProdutosDeOutraEmpresa() throws Exception {
        catalogo.guardar(usuarioA, p1);
        catalogo.guardar(usuarioA, p2);
        catalogo.guardar(usuarioB, p1);
        catalogo.guardar(usuarioB, deOutra);

        resumo(tokenEmpresa).andExpect(jsonPath("$.produtosEmEstantes").value(3));
        resumo(tokenOutraEmpresa).andExpect(jsonPath("$.produtosEmEstantes").value(1));
    }

    @Test
    void produtoDesativadoNaoConta() throws Exception {
        catalogo.guardar(usuarioA, p1);
        catalogo.guardar(usuarioA, p2);
        jdbc.update("UPDATE empresa_produto SET ativo = FALSE WHERE id_empresa = ? AND id_produto = ?", empresa, p2);

        resumo(tokenEmpresa).andExpect(jsonPath("$.produtosEmEstantes").value(1));
    }

    @Test
    void taxaDeSucessoIgnoraNaoAvaliadasEContaMisturaUmaVez() throws Exception {
        catalogo.historicoMatch(usuarioA, p1, p2, "compativel", null, null, null); // A e B da empresa: conta 1
        catalogo.historicoMatch(usuarioA, p1, deOutra, "compativel", null, null, null);
        catalogo.historicoMatch(usuarioB, p2, deOutra, "incompativel", "alta", "Risco", null);
        catalogo.historicoMatch(usuarioB, p1, deOutra, "nao_avaliado", null, "Sem FDS", null);
        int semDono = catalogo.produto("Sem empresa", catalogo.marca("Outra marca", null), "outro", null, null);
        catalogo.historicoMatch(usuarioB, deOutra, semDono, "compativel", null, null, null); // nenhum produto da empresa

        resumo(tokenEmpresa)
                .andExpect(jsonPath("$.misturas.total").value(4))
                .andExpect(jsonPath("$.misturas.bemSucedidas").value(2))
                .andExpect(jsonPath("$.misturas.malSucedidas").value(1))
                .andExpect(jsonPath("$.misturas.naoAvaliadas").value(1))
                .andExpect(jsonPath("$.misturas.taxaSucesso").value(0.6667));
    }

    @Test
    void soNaoAvaliadasNaoTemTaxa() throws Exception {
        catalogo.historicoMatch(usuarioA, p1, p2, "nao_avaliado", null, "Sem FDS", null);

        resumo(tokenEmpresa)
                .andExpect(jsonPath("$.misturas.total").value(1))
                .andExpect(jsonPath("$.misturas.taxaSucesso").doesNotExist());
    }

    @Test
    void produtoDestaqueEOVinculoMaisRecente() throws Exception {
        jdbc.update("UPDATE empresa_produto SET data_cadastro = TIMESTAMP '2026-01-01 10:00:00' WHERE id_empresa = ? AND id_produto = ?", empresa, p1);
        jdbc.update("UPDATE empresa_produto SET data_cadastro = TIMESTAMP '2026-03-01 10:00:00' WHERE id_empresa = ? AND id_produto = ?", empresa, p2);

        resumo(tokenEmpresa)
                .andExpect(jsonPath("$.produtoDestaque.id").value(p2))
                .andExpect(jsonPath("$.produtoDestaque.nome").value("Produto 2"))
                .andExpect(jsonPath("$.produtoDestaque.marca.nome").value("Marca"));
    }

    @Test
    void respostaNaoTemDadosDeUsuario() throws Exception {
        catalogo.guardar(usuarioA, p1);
        catalogo.historicoMatch(usuarioA, p1, p2, "compativel", null, null, null);

        String corpo = resumo(tokenEmpresa).andReturn().getResponse().getContentAsString();

        assertThat(corpo)
                .doesNotContain(usuarioA.toString())
                .doesNotContain("a@insights.test")
                .doesNotContainIgnoringCase("usuario");
    }

    @Test
    void autenticacaoEEmpresaInativa() throws Exception {
        mvc.perform(get("/api/v1/empresas/me/resumo")).andExpect(status().isUnauthorized());
        String usuario = TestLogins.usuario(mvc, "c@insights.test");
        resumo(usuario).andExpect(status().isForbidden());

        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE id = ?", empresa);
        resumo(tokenEmpresa)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("empresa_inativa"));
    }

    // --- GET /empresas/me/estatisticas ---

    @Test
    void devolveOsDoisCardsNaOrdemDoContrato() throws Exception {
        catalogo.guardar(usuarioA, p1);
        catalogo.historicoMatch(usuarioA, p1, p2, "compativel", null, null, null);
        catalogo.historicoMatch(usuarioA, p1, deOutra, "incompativel", "alta", "Risco", null);

        estatisticas(tokenEmpresa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(2))
                .andExpect(jsonPath("$.cards[0].id").value("produtos_em_estantes"))
                .andExpect(jsonPath("$.cards[0].titulo").value("Produtos em estantes"))
                .andExpect(jsonPath("$.cards[0].tipo").value("numero"))
                .andExpect(jsonPath("$.cards[0].valor").value(1))
                .andExpect(jsonPath("$.cards[0].series.length()").value(0))
                .andExpect(jsonPath("$.cards[1].id").value("taxa_sucesso_misturas"))
                .andExpect(jsonPath("$.cards[1].tipo").value("rosca"))
                .andExpect(jsonPath("$.cards[1].valor").doesNotExist())
                .andExpect(jsonPath("$.cards[1].series[0].rotulo").value("Bem sucedida"))
                .andExpect(jsonPath("$.cards[1].series[0].valor").value(1))
                .andExpect(jsonPath("$.cards[1].series[1].rotulo").value("Mal sucedida"))
                .andExpect(jsonPath("$.cards[1].series[1].valor").value(1));
    }

    @Test
    void buscaPorTituloSemAcentoNemCaixa() throws Exception {
        estatisticas(tokenEmpresa, "TAXA").andExpect(jsonPath("$.cards.length()").value(1))
                .andExpect(jsonPath("$.cards[0].id").value("taxa_sucesso_misturas"));
        estatisticas(tokenEmpresa, "estantes").andExpect(jsonPath("$.cards[0].id").value("produtos_em_estantes"));
        estatisticas(tokenEmpresa, "").andExpect(jsonPath("$.cards.length()").value(2));
        estatisticas(tokenEmpresa, "nada").andExpect(jsonPath("$.cards.length()").value(0));
        estatisticas(tokenEmpresa, "%").andExpect(jsonPath("$.cards.length()").value(0));
    }

    @Test
    void termoMuitoLongoDa400() throws Exception {
        estatisticas(tokenEmpresa, "a".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("termo_muito_longo"));
    }

    @Test
    void empresaSemDadosVeZerosNasEstatisticas() throws Exception {
        estatisticas(tokenOutraEmpresa)
                .andExpect(jsonPath("$.cards[0].valor").value(0))
                .andExpect(jsonPath("$.cards[1].series[0].valor").value(0))
                .andExpect(jsonPath("$.cards[1].series[1].valor").value(0));
    }

    @Test
    void autenticacaoEEmpresaInativaNasEstatisticas() throws Exception {
        mvc.perform(get("/api/v1/empresas/me/estatisticas")).andExpect(status().isUnauthorized());
        estatisticas(TestLogins.usuario(mvc, "d@insights.test")).andExpect(status().isForbidden());
        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE id = ?", empresa);
        estatisticas(tokenEmpresa).andExpect(jsonPath("$.detail").value("empresa_inativa"));
    }
}
