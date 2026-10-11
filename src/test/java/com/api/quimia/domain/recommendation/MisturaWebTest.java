package com.api.quimia.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(RecommendationDoubles.class)
class MisturaWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestCatalogo catalogo;
    private String tokenA;
    private String tokenB;
    private UUID usuarioA;
    private UUID usuarioB;
    private int alvejante;
    private int limpaVidros;
    private int desengordurante;
    private int doOutro;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@mistura.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@mistura.test'");
        tokenA = TestLogins.usuario(mvc, "a@mistura.test");
        tokenB = TestLogins.usuario(mvc, "b@mistura.test");
        usuarioA = catalogo.usuarioId("a@mistura.test");
        usuarioB = catalogo.usuarioId("b@mistura.test");
        int marca = catalogo.marca("Marca", null);
        alvejante = catalogo.produto("Alvejante", marca, "alvejante", null, null);
        limpaVidros = catalogo.produto("Limpa vidros", marca, "limpeza_geral", null, null);
        desengordurante = catalogo.produto("Desengordurante", marca, "desengraxante", null, null);
        doOutro = catalogo.produto("Do outro", marca, "outro", null, null);
        for (int produto : new int[] {alvejante, limpaVidros, desengordurante}) {
            catalogo.guardar(usuarioA, produto);
        }
        catalogo.guardar(usuarioB, doOutro);
        RecommendationDoubles.programar("compativel", null, null, null);
    }

    private ResultActions misturar(String token, Object a, Object b) throws Exception {
        return mvc.perform(post("/api/v1/misturas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoIdA\":" + a + ",\"produtoIdB\":" + b + "}"));
    }

    // --- POST /misturas ---

    @Test
    void misturaIncompativelGravaOHistoricoEDevolveOResultado() throws Exception {
        RecommendationDoubles.programar("incompativel", "critica", "Libera gás tóxico", null);

        misturar(tokenA, limpaVidros, alvejante)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/misturas/\\d+")))
                .andExpect(jsonPath("$.resultado").value("incompativel"))
                .andExpect(jsonPath("$.severidade").value("critica"))
                .andExpect(jsonPath("$.descricaoRisco").value("Libera gás tóxico"))
                .andExpect(jsonPath("$.produtoA.id").value(Math.min(alvejante, limpaVidros)))
                .andExpect(jsonPath("$.produtoB.id").value(Math.max(alvejante, limpaVidros)))
                .andExpect(jsonPath("$.consultadoEm").exists())
                .andExpect(jsonPath("$.dosagem").doesNotExist());
        Integer linhas = jdbc.queryForObject(
                "SELECT count(*) FROM historico_match WHERE id_usuario = ? AND id_produto_a < id_produto_b", Integer.class, usuarioA);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void misturaCompativelNaoTemSeveridadeNemDescricao() throws Exception {
        misturar(tokenA, alvejante, limpaVidros)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("compativel"))
                .andExpect(jsonPath("$.severidade").doesNotExist())
                .andExpect(jsonPath("$.descricaoRisco").doesNotExist());
    }

    @Test
    void naoAvaliadoGravaADescricaoDoMotivo() throws Exception {
        RecommendationDoubles.programar("nao_avaliado", null, "Composição incompleta: há compostos não identificados", null);

        misturar(tokenA, alvejante, limpaVidros)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("nao_avaliado"))
                .andExpect(jsonPath("$.descricaoRisco").value("Composição incompleta: há compostos não identificados"));
    }

    @Test
    void repetirCriaOutraLinhaENaoImportaAOrdem() throws Exception {
        misturar(tokenA, alvejante, limpaVidros).andExpect(status().isCreated());
        misturar(tokenA, limpaVidros, alvejante).andExpect(status().isCreated());

        Integer linhas = jdbc.queryForObject("SELECT count(*) FROM historico_match WHERE id_usuario = ?", Integer.class, usuarioA);
        assertThat(linhas).isEqualTo(2);
    }

    @Test
    void produtosIguaisDao422() throws Exception {
        misturar(tokenA, alvejante, alvejante)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("produtos_iguais"));
    }

    @Test
    void produtoForaDaEstanteInexistenteOuDeOutroUsuarioDa404() throws Exception {
        for (int fora : new int[] {doOutro, 999999}) {
            misturar(tokenA, alvejante, fora)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("produto_nao_esta_na_estante"));
        }
        misturar(tokenB, alvejante, doOutro)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_esta_na_estante"));
    }

    @Test
    void usuarioDoCorpoNuncaEUsado() throws Exception {
        mvc.perform(post("/api/v1/misturas").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoIdA\":" + alvejante + ",\"produtoIdB\":" + limpaVidros
                                + ",\"idUsuario\":\"" + usuarioB + "\"}"))
                .andExpect(status().isCreated());

        Integer doB = jdbc.queryForObject("SELECT count(*) FROM historico_match WHERE id_usuario = ?", Integer.class, usuarioB);
        assertThat(doB).isZero();
    }

    @Test
    void validacoesDeCorpo() throws Exception {
        mvc.perform(post("/api/v1/misturas").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("validation"));
        misturar(tokenA, "1.5", alvejante)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("corpo_invalido"));
        misturar(tokenA, "\"abc\"", alvejante).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/misturas").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIdA\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("corpo_invalido"));
        mvc.perform(post("/api/v1/misturas").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void autenticacao() throws Exception {
        mvc.perform(post("/api/v1/misturas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@mistura.test", "30000003000196");
        misturar(empresa, alvejante, limpaVidros).andExpect(status().isForbidden());
    }

    // --- GET /misturas e /misturas/{id} ---

    @Test
    void listaSoDoUsuarioDaMaisRecenteParaAMaisAntiga() throws Exception {
        int primeira = catalogo.historicoMatch(usuarioA, alvejante, limpaVidros, "compativel", null, null, null);
        jdbc.update("UPDATE historico_match SET data_consulta = TIMESTAMP '2026-01-01 10:00:00' WHERE id = ?", primeira);
        int segunda = catalogo.historicoMatch(usuarioA, alvejante, desengordurante, "nao_avaliado", null, "Sem FDS", null);
        jdbc.update("UPDATE historico_match SET data_consulta = TIMESTAMP '2026-02-01 10:00:00' WHERE id = ?", segunda);
        catalogo.historicoMatch(usuarioB, alvejante, limpaVidros, "compativel", null, null, null);

        mvc.perform(get("/api/v1/misturas").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(2))
                .andExpect(jsonPath("$.itens[0].id").value(segunda))
                .andExpect(jsonPath("$.itens[1].id").value(primeira))
                .andExpect(jsonPath("$.itens[0].produtoA.nome").exists());
    }

    @Test
    void filtraPorProdutoEmQualquerLado() throws Exception {
        catalogo.historicoMatch(usuarioA, alvejante, limpaVidros, "compativel", null, null, null);
        catalogo.historicoMatch(usuarioA, limpaVidros, desengordurante, "compativel", null, null, null);

        mvc.perform(get("/api/v1/misturas").param("produtoId", String.valueOf(limpaVidros)).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.totalItens").value(2));
        mvc.perform(get("/api/v1/misturas").param("produtoId", String.valueOf(alvejante)).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void historicoVazioDevolveListaVazia() throws Exception {
        mvc.perform(get("/api/v1/misturas").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0))
                .andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void paginacaoInvalidaDa400() throws Exception {
        for (String[] par : new String[][] {{"size", "51"}, {"size", "0"}, {"page", "-1"}, {"page", "2147483647"}}) {
            mvc.perform(get("/api/v1/misturas").param(par[0], par[1]).header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("paginacao_invalida"));
        }
        mvc.perform(get("/api/v1/misturas").param("produtoId", "abc").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("parametro_invalido"));
    }

    @Test
    void consultaPorIdEIsolaUsuarios() throws Exception {
        int id = catalogo.historicoMatch(usuarioA, alvejante, limpaVidros, "incompativel", "alta", "Risco", null);

        mvc.perform(get("/api/v1/misturas/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severidade").value("alta"));
        mvc.perform(get("/api/v1/misturas/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("mistura_nao_encontrada"));
        mvc.perform(get("/api/v1/misturas/999999").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("mistura_nao_encontrada"));
        mvc.perform(get("/api/v1/misturas/abc").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("parametro_invalido"));
    }
}
