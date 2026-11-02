package com.api.quimia.domain.kpi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

/**
 * Os objetos do data mart ({@code vw_fato_historico_recomendacao}, {@code dim_usuario}) não existem no schema de
 * teste: aqui entram como tabelas com as colunas confirmadas no banco real.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class KpiEmpresaWebTest {
    private static final List<String> ROTAS = List.of("dau", "recomendacoes", "top-produtos", "perfil-usuarios");
    private static final LocalDate DIA_1 = LocalDate.of(2026, 10, 26);
    private static final LocalDate DIA_2 = DIA_1.plusDays(1);
    private static final LocalDate DIA_3 = DIA_1.plusDays(2);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenEmpresa;
    private String tokenOutraEmpresa;
    private int empresa;
    private int outraEmpresa;
    private int produtoA;
    private int produtoB;
    private int produtoDaOutra;
    private UUID usuario1;
    private UUID usuario2;
    private UUID usuario3;
    private UUID usuarioDaOutra;
    private int proximoFato;

    @BeforeEach
    void seed() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS dim_usuario (id_usuario UUID PRIMARY KEY, nivel_acesso VARCHAR(50), "
                + "estado VARCHAR(2))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS vw_fato_historico_recomendacao (id_fato INT PRIMARY KEY, data DATE, "
                + "id_produto INT, nome_produto VARCHAR(255), nome_marca VARCHAR(255), id_usuario UUID, "
                + "nivel_acesso VARCHAR(50), resultado VARCHAR(50))");
        jdbc.update("DELETE FROM vw_fato_historico_recomendacao");
        jdbc.update("DELETE FROM dim_usuario");
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@kpi.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@kpi.test'");
        tokenEmpresa = TestLogins.empresa(mvc, "Empresa Um", "um@kpi.test", "30000031000103");
        tokenOutraEmpresa = TestLogins.empresa(mvc, "Empresa Dois", "dois@kpi.test", "30000032000158");
        empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'um@kpi.test'", Integer.class);
        outraEmpresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'dois@kpi.test'", Integer.class);
        for (String email : List.of("u1@kpi.test", "u2@kpi.test", "u3@kpi.test", "u4@kpi.test")) {
            TestLogins.usuario(mvc, email);
        }
        usuario1 = catalogo.usuarioId("u1@kpi.test");
        usuario2 = catalogo.usuarioId("u2@kpi.test");
        usuario3 = catalogo.usuarioId("u3@kpi.test");
        usuarioDaOutra = catalogo.usuarioId("u4@kpi.test");
        int marca = catalogo.marca("Marca Kpi", null);
        produtoA = catalogo.produto("Produto A", marca, "limpeza_geral", null, null);
        produtoB = catalogo.produto("Produto B", marca, "desinfetante", null, null);
        produtoDaOutra = catalogo.produto("Produto da outra", marca, "outro", null, null);
        catalogo.vinculo(empresa, produtoA, "TRUE");
        catalogo.vinculo(empresa, produtoB, "NULL");
        catalogo.vinculo(outraEmpresa, produtoDaOutra, "TRUE");
        perfil(usuario1, "comum", "SP");
        perfil(usuario2, "comum", null);
        perfil(usuario3, "avancado", "SP");
        perfil(usuarioDaOutra, "comum", "RJ");
    }

    private void perfil(UUID usuario, String nivel, String estado) {
        jdbc.update("INSERT INTO dim_usuario (id_usuario, nivel_acesso, estado) VALUES (?, ?, ?)", usuario, nivel, estado);
    }

    private void consulta(LocalDate dia, int produto, String nomeProduto, UUID usuario) {
        jdbc.update(
                "INSERT INTO vw_fato_historico_recomendacao (id_fato, data, id_produto, nome_produto, nome_marca, "
                        + "id_usuario) VALUES (?, ?, ?, ?, 'Marca Kpi', ?)",
                ++proximoFato, dia, produto, nomeProduto, usuario);
    }

    /** Dia 1 com 1 usuário, dia 2 com 3, dia 3 com 2 (um deles repete a consulta). */
    private void consultasDaEmpresa() {
        consulta(DIA_1, produtoA, "Produto A", usuario1);
        consulta(DIA_2, produtoA, "Produto A", usuario1);
        consulta(DIA_2, produtoA, "Produto A", usuario2);
        consulta(DIA_2, produtoB, "Produto B", usuario3);
        consulta(DIA_3, produtoA, "Produto A", usuario1);
        consulta(DIA_3, produtoA, "Produto A", usuario1);
        consulta(DIA_3, produtoB, "Produto B", usuario3);
    }

    private ResultActions kpi(String token, String rota) throws Exception {
        return mvc.perform(get("/api/v1/empresas/me/kpis/" + rota).header("Authorization", "Bearer " + token));
    }

    // --- GET /empresas/me/kpis/dau ---

    @Test
    void dauTrazVariacaoMediasDesvioEZScore() throws Exception {
        consultasDaEmpresa();

        kpi(tokenEmpresa, "dau")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].data").value("2026-10-26"))
                .andExpect(jsonPath("$[0].usuariosAtivos").value(1))
                .andExpect(jsonPath("$[0].variacaoUsuarios").doesNotExist())
                .andExpect(jsonPath("$[0].variacaoPct").doesNotExist())
                .andExpect(jsonPath("$[1].usuariosAtivos").value(3))
                .andExpect(jsonPath("$[1].variacaoUsuarios").value(2))
                .andExpect(jsonPath("$[1].variacaoPct").value(200.0))
                .andExpect(jsonPath("$[2].usuariosAtivos").value(2))
                .andExpect(jsonPath("$[2].variacaoUsuarios").value(-1))
                .andExpect(jsonPath("$[2].variacaoPct").value(-33.33))
                .andExpect(jsonPath("$[2].mediaGlobal").value(2.0))
                .andExpect(jsonPath("$[0].media7d").value(1.0))
                .andExpect(jsonPath("$[1].media7d").value(2.0))
                .andExpect(jsonPath("$[2].media7d").value(2.0))
                .andExpect(jsonPath("$[2].desvioPadrao").value(1.0))
                .andExpect(jsonPath("$[0].zScore").value(-1.0))
                .andExpect(jsonPath("$[1].zScore").value(1.0))
                .andExpect(jsonPath("$[2].zScore").value(0.0));
    }

    @Test
    void dauDeUmDiaSoNaoTemDesvioNemZScore() throws Exception {
        consulta(DIA_1, produtoA, "Produto A", usuario1);

        kpi(tokenEmpresa, "dau")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].usuariosAtivos").value(1))
                .andExpect(jsonPath("$[0].desvioPadrao").doesNotExist())
                .andExpect(jsonPath("$[0].zScore").doesNotExist());
    }

    // --- GET /empresas/me/kpis/recomendacoes ---

    @Test
    void recomendacoesContaConsultasEUsuariosPorDia() throws Exception {
        consultasDaEmpresa();

        kpi(tokenEmpresa, "recomendacoes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].totalConsultas").value(1))
                .andExpect(jsonPath("$[0].usuariosDistintos").value(1))
                .andExpect(jsonPath("$[1].totalConsultas").value(3))
                .andExpect(jsonPath("$[1].usuariosDistintos").value(3))
                .andExpect(jsonPath("$[2].totalConsultas").value(3))
                .andExpect(jsonPath("$[2].usuariosDistintos").value(2));
    }

    // --- GET /empresas/me/kpis/top-produtos ---

    @Test
    void topProdutosOrdenaPorConsultasEAceitaLimite() throws Exception {
        consultasDaEmpresa();

        kpi(tokenEmpresa, "top-produtos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idProduto").value(produtoA))
                .andExpect(jsonPath("$[0].nomeProduto").value("Produto A"))
                .andExpect(jsonPath("$[0].nomeMarca").value("Marca Kpi"))
                .andExpect(jsonPath("$[0].totalConsultas").value(5))
                .andExpect(jsonPath("$[0].ranking").value(1))
                .andExpect(jsonPath("$[1].totalConsultas").value(2))
                .andExpect(jsonPath("$[1].ranking").value(2));
        mvc.perform(get("/api/v1/empresas/me/kpis/top-produtos")
                        .param("limite", "1")
                        .header("Authorization", "Bearer " + tokenEmpresa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idProduto").value(produtoA));
    }

    @Test
    void limiteForaDeUmAcinquentaEhRecusado() throws Exception {
        for (String limite : List.of("0", "-1", "51")) {
            mvc.perform(get("/api/v1/empresas/me/kpis/top-produtos")
                            .param("limite", limite)
                            .header("Authorization", "Bearer " + tokenEmpresa))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("limite_invalido"));
        }
        for (String limite : List.of("1", "50")) {
            mvc.perform(get("/api/v1/empresas/me/kpis/top-produtos")
                            .param("limite", limite)
                            .header("Authorization", "Bearer " + tokenEmpresa))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void limiteQueNaoEhNumeroEhRecusado() throws Exception {
        mvc.perform(get("/api/v1/empresas/me/kpis/top-produtos")
                        .param("limite", "muitos")
                        .header("Authorization", "Bearer " + tokenEmpresa))
                .andExpect(status().isBadRequest());
    }

    // --- GET /empresas/me/kpis/perfil-usuarios ---

    @Test
    void perfilContaUsuariosDistintosPorNivelEEstado() throws Exception {
        consultasDaEmpresa();

        kpi(tokenEmpresa, "perfil-usuarios")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.nivelAcesso=='comum' && @.estado=='SP')].totalUsuarios").value(1))
                .andExpect(jsonPath("$[?(@.nivelAcesso=='comum' && @.estado=='NI')].totalUsuarios").value(1))
                .andExpect(jsonPath("$[?(@.nivelAcesso=='avancado' && @.estado=='SP')].totalUsuarios").value(1))
                .andExpect(jsonPath("$[0].nome").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    // --- isolamento entre empresas ---

    @Test
    void umaEmpresaNuncaEnxergaLinhasDaOutra() throws Exception {
        consultasDaEmpresa();
        consulta(DIA_1, produtoDaOutra, "Produto da outra", usuarioDaOutra);
        consulta(DIA_1, produtoDaOutra, "Produto da outra", usuarioDaOutra);
        consulta(DIA_2, produtoDaOutra, "Produto da outra", usuarioDaOutra);

        kpi(tokenEmpresa, "top-produtos")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.idProduto==" + produtoDaOutra + ")]").isEmpty());
        kpi(tokenEmpresa, "dau")
                .andExpect(jsonPath("$[0].usuariosAtivos").value(1))
                .andExpect(jsonPath("$[1].usuariosAtivos").value(3));
        kpi(tokenEmpresa, "recomendacoes")
                .andExpect(jsonPath("$[0].totalConsultas").value(1))
                .andExpect(jsonPath("$[1].totalConsultas").value(3));
        kpi(tokenEmpresa, "perfil-usuarios")
                .andExpect(jsonPath("$[?(@.estado=='RJ')]").isEmpty());

        kpi(tokenOutraEmpresa, "top-produtos")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idProduto").value(produtoDaOutra))
                .andExpect(jsonPath("$[0].totalConsultas").value(3));
        kpi(tokenOutraEmpresa, "dau")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].usuariosAtivos").value(1))
                .andExpect(jsonPath("$[1].usuariosAtivos").value(1));
        kpi(tokenOutraEmpresa, "recomendacoes")
                .andExpect(jsonPath("$[0].totalConsultas").value(2))
                .andExpect(jsonPath("$[1].totalConsultas").value(1));
        kpi(tokenOutraEmpresa, "perfil-usuarios")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("RJ"));
    }

    @Test
    void idDeOutraEmpresaNaQueryStringNaoMudaOResultado() throws Exception {
        consultasDaEmpresa();
        consulta(DIA_1, produtoDaOutra, "Produto da outra", usuarioDaOutra);

        mvc.perform(get("/api/v1/empresas/me/kpis/top-produtos")
                        .param("idEmpresa", String.valueOf(outraEmpresa))
                        .param("empresaId", String.valueOf(outraEmpresa))
                        .header("Authorization", "Bearer " + tokenEmpresa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idProduto==" + produtoDaOutra + ")]").isEmpty());
    }

    // --- vínculo e lista vazia ---

    @Test
    void vinculoInativoNaoContaEVinculoNuloConta() throws Exception {
        consultasDaEmpresa();
        jdbc.update("UPDATE empresa_produto SET ativo = FALSE WHERE id_empresa = ? AND id_produto = ?", empresa, produtoA);

        kpi(tokenEmpresa, "top-produtos")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idProduto").value(produtoB))
                .andExpect(jsonPath("$[0].totalConsultas").value(2));
    }

    @Test
    void empresaSemConsultasRecebeListasVazias() throws Exception {
        for (String rota : ROTAS) {
            kpi(tokenEmpresa, rota).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
    }

    // --- autenticação e empresa ---

    @Test
    void semTokenDa401EUsuarioDoAppDa403() throws Exception {
        String usuario = TestLogins.usuario(mvc, "u5@kpi.test");
        for (String rota : ROTAS) {
            mvc.perform(get("/api/v1/empresas/me/kpis/" + rota)).andExpect(status().isUnauthorized());
            kpi(usuario, rota).andExpect(status().isForbidden());
        }
    }

    @Test
    void empresaDesativadaDa403() throws Exception {
        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE id = ?", empresa);

        for (String rota : ROTAS) {
            kpi(tokenEmpresa, rota)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("empresa_inativa"));
        }
    }

    @Test
    void empresaApagadaComTokenAindaValidoDa404() throws Exception {
        String token = TestLogins.empresa(mvc, "Apagada", "apagada@kpi.test", "30000066000142");
        jdbc.update("DELETE FROM empresa WHERE email = 'apagada@kpi.test'");

        for (String rota : ROTAS) {
            kpi(token, rota)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("empresa_nao_encontrada"));
        }
    }
}
