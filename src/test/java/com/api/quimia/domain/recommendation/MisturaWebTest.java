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
import java.time.LocalDate;
import java.util.UUID;
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

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
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
    private int sabao;
    private int semFds;
    private int doOutro;
    private int regraCloroAmonia;

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
        int hipoclorito = catalogo.substancia("Hipoclorito de sódio", "7681-52-9", null);
        int amonia = catalogo.substancia("Amônia", "7664-41-7", null);
        int agua = catalogo.substancia("Água", "7732-18-5", null);
        regraCloroAmonia = catalogo.regra(hipoclorito, null, amonia, null, "critica", "Libera gás tóxico", true);
        alvejante = produtoCom(marca, "Alvejante", hipoclorito);
        limpaVidros = produtoCom(marca, "Limpa vidros", amonia);
        desengordurante = produtoCom(marca, "Desengordurante", agua, null);
        sabao = produtoCom(marca, "Sabão", agua);
        semFds = catalogo.produto("Sem FDS", marca, "outro", null, null);
        doOutro = produtoCom(marca, "Do outro", agua);
        for (int produto : new int[] {alvejante, limpaVidros, desengordurante, sabao, semFds}) {
            catalogo.guardar(usuarioA, produto);
        }
        catalogo.guardar(usuarioB, doOutro);
    }

    /** Produto com FDS ativa; `null` entre as substâncias é um composto não identificado. */
    private int produtoCom(int marca, String nome, Integer... substancias) {
        int produto = catalogo.produto(nome, marca, "limpeza_geral", null, null);
        int fds = catalogo.fds(produto, "1", LocalDate.of(2026, 1, 1), true, null);
        for (Integer substancia : substancias) {
            catalogo.composto(fds, substancia, "1.00", "5.00", "%");
        }
        return produto;
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
        var linha = jdbc.queryForMap(
                "SELECT id_produto_a, id_produto_b, resultado, severidade, id_regra, descricao_risco FROM historico_match"
                        + " WHERE id_usuario = ?",
                usuarioA);
        assertThat(((Number) linha.get("id_produto_a")).intValue()).isEqualTo(Math.min(alvejante, limpaVidros));
        assertThat(((Number) linha.get("id_produto_b")).intValue()).isEqualTo(Math.max(alvejante, limpaVidros));
        assertThat(linha.get("resultado")).isEqualTo("incompativel");
        assertThat(linha.get("severidade")).isEqualTo("critica");
        assertThat(((Number) linha.get("id_regra")).intValue()).isEqualTo(regraCloroAmonia);
        assertThat(linha.get("descricao_risco")).isEqualTo("Libera gás tóxico");
    }

    @Test
    void historicoGuardaOResultadoDoMomentoDaConsulta() throws Exception {
        misturar(tokenA, alvejante, limpaVidros).andExpect(jsonPath("$.resultado").value("incompativel"));
        jdbc.update("UPDATE incompatibilidade_regra SET ativo = FALSE WHERE id = ?", regraCloroAmonia);

        misturar(tokenA, alvejante, limpaVidros).andExpect(jsonPath("$.resultado").value("compativel"));

        assertThat(jdbc.queryForList(
                        "SELECT resultado FROM historico_match WHERE id_usuario = ? ORDER BY id", String.class, usuarioA))
                .containsExactly("incompativel", "compativel");
    }

    @Test
    void misturaCompativelNaoTemSeveridadeNemDescricao() throws Exception {
        misturar(tokenA, alvejante, sabao)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("compativel"))
                .andExpect(jsonPath("$.severidade").doesNotExist())
                .andExpect(jsonPath("$.descricaoRisco").doesNotExist());
    }

    @Test
    void naoAvaliadoGravaADescricaoDoMotivo() throws Exception {
        misturar(tokenA, limpaVidros, desengordurante)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("nao_avaliado"))
                .andExpect(jsonPath("$.severidade").doesNotExist())
                .andExpect(jsonPath("$.descricaoRisco").value("Composição incompleta: há compostos não identificados"));
        misturar(tokenA, alvejante, semFds)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("nao_avaliado"))
                .andExpect(jsonPath("$.descricaoRisco").value("Produto sem composição resolvida"));
    }

    @Test
    void compostoNaoIdentificadoNaoEscondeRegraQueCasa() throws Exception {
        misturar(tokenA, alvejante, desengordurante).andExpect(jsonPath("$.resultado").value("nao_avaliado"));
        int hipoclorito = jdbc.queryForObject("SELECT id FROM substancia WHERE cas_numero = '7681-52-9'", Integer.class);
        int agua = jdbc.queryForObject("SELECT id FROM substancia WHERE cas_numero = '7732-18-5'", Integer.class);
        catalogo.regra(agua, null, hipoclorito, null, "baixa", "Dilui", true);

        misturar(tokenA, alvejante, desengordurante)
                .andExpect(jsonPath("$.resultado").value("incompativel"))
                .andExpect(jsonPath("$.severidade").value("baixa"));
    }

    @Test
    void repetirCriaOutraLinhaENaoImportaAOrdem() throws Exception {
        misturar(tokenA, alvejante, limpaVidros).andExpect(status().isCreated());
        misturar(tokenA, limpaVidros, alvejante).andExpect(status().isCreated());

        assertThat(jdbc.queryForList(
                        "SELECT DISTINCT id_produto_a FROM historico_match WHERE id_usuario = ?", Integer.class, usuarioA))
                .containsExactly(Math.min(alvejante, limpaVidros));

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
        for (int fora : new int[] {doOutro, 999999, -1, 0, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
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
        misturar(tokenA, "2147483648", alvejante)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("corpo_invalido"));
        misturar(tokenA, "null", alvejante)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("validation"));
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
    void paginacaoNosLimites() throws Exception {
        for (int i = 0; i < 3; i++) {
            catalogo.historicoMatch(usuarioA, alvejante, limpaVidros, "compativel", null, null, null);
        }

        mvc.perform(get("/api/v1/misturas").param("page", "1").param("size", "2").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.totalItens").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        for (String tamanho : new String[] {"1", "50"}) {
            mvc.perform(get("/api/v1/misturas").param("size", tamanho).header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/misturas").param("page", "42949672").param("size", "50")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));
        mvc.perform(get("/api/v1/misturas").param("page", "2147483647").param("size", "1")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));
        mvc.perform(get("/api/v1/misturas").param("page", "42949673").param("size", "50")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("paginacao_invalida"));
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
