package com.api.quimia.domain.safety;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assumptions;
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
 * Resumo do rótulo e substâncias (somente leitura) pela pilha HTTP completa. Roda em H2 no build e
 * no PostgreSQL com o DDL real do quimia-bd via scripts/local-validation/run-pg-tests.sh.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class SegurancaWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenA;
    private String tokenB;
    private String tokenEmpresa;
    private int limpaVidros;
    private int aguaSanitaria;
    private int semFds;
    private int oculto;
    private int hipoclorito;
    private int hidroxido;
    private int etanol;

    @BeforeEach
    void seed() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@seguranca.test'");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@seguranca.test'");
        tokenA = TestLogins.usuario(mvc, "a@seguranca.test");
        tokenB = TestLogins.usuario(mvc, "b@seguranca.test");
        tokenEmpresa = TestLogins.empresa(mvc, "Empresa Seg", "empresa@seguranca.test", "77.889.900/0001-66");

        int marca = catalogo.marca("Marca Segura", null);
        limpaVidros = catalogo.produto("Limpa Vidros", marca, "limpeza_geral", "7896000000001", null);
        aguaSanitaria = catalogo.produto("Água Sanitária", marca, "alvejante", "7896000000002", null);
        semFds = catalogo.produto("Sem Fds", marca, "outro", "7896000000003", null);
        oculto = catalogo.produto("Oculto Depois", marca, "outro", "7896000000004", null);

        int corrosivo = catalogo.classe("Corrosivo", "Corrói tecidos e metais");
        int oxidante = catalogo.classe("Oxidante", "Favorece a combustão");
        hipoclorito = catalogo.substancia("Hipoclorito de sódio", "7681-52-9", "Alvejante e desinfetante");
        hidroxido = catalogo.substancia("Hidróxido de sódio", "1310-73-2", null);
        etanol = catalogo.substancia("Etanol", "64-17-5", null);
        catalogo.substanciaClasse(hipoclorito, oxidante);
        catalogo.substanciaClasse(hipoclorito, corrosivo);
        catalogo.substanciaClasse(hidroxido, corrosivo);
        catalogo.sinonimo(hipoclorito, "Cloro ativo");
        catalogo.sinonimo(hipoclorito, "Água de Javel");

        // Limpa Vidros: a FDS mais recente e ativa vale; a antiga e a inativa (mais nova) não.
        int antiga = catalogo.fds(limpaVidros, "v1", LocalDate.of(2023, 1, 1), true, "Resumo antigo");
        int vigente = catalogo.fds(limpaVidros, "v2", LocalDate.of(2024, 6, 1), true, "Evite misturar com ácidos.");
        int inativa = catalogo.fds(limpaVidros, "v3", LocalDate.of(2025, 1, 1), false, "Resumo inativo");
        catalogo.composto(antiga, etanol, "10.00", "20.00", "%");
        catalogo.composto(vigente, hipoclorito, "5.00", "6.00", "%");
        catalogo.composto(vigente, hidroxido, "1.00", null, "%");
        catalogo.composto(vigente, null, null, null, null);
        catalogo.composto(inativa, etanol, "1.00", "1.00", "%");

        int agua = catalogo.fds(aguaSanitaria, "v1", LocalDate.of(2024, 3, 1), true, null);
        catalogo.composto(agua, hipoclorito, "2.50", "2.50", "%");

        int fdsOculto = catalogo.fds(oculto, "v1", LocalDate.of(2024, 1, 1), true, "   ");
        catalogo.composto(fdsOculto, etanol, null, null, null);
    }

    @Test
    void resumoDaFdsAtivaMaisRecenteDeUmProdutoDaEstante() throws Exception {
        guardar(tokenA, limpaVidros);

        resumo(tokenA, limpaVidros)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtoId").value(limpaVidros))
                .andExpect(jsonPath("$.resumo").value("Evite misturar com ácidos."))
                .andExpect(jsonPath("$.versao").value("v2"))
                .andExpect(jsonPath("$.dataAtualizacao").value("2024-06-01"));
    }

    @Test
    void semTextoOuSemFdsRespondeOkComResumoNulo() throws Exception {
        guardar(tokenA, aguaSanitaria, semFds);

        resumo(tokenA, aguaSanitaria)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.versao").value("v1"));
        resumo(tokenA, semFds)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtoId").value(semFds))
                .andExpect(jsonPath("$.resumo").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.versao").value(Matchers.nullValue()));
    }

    @Test
    void resumoSoDeProdutoNaEstanteDoProprioUsuario() throws Exception {
        guardar(tokenA, limpaVidros);

        resumo(tokenB, limpaVidros)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_esta_na_estante"));
        resumo(tokenA, aguaSanitaria).andExpect(status().isNotFound());
        resumo(tokenA, 999999).andExpect(status().isNotFound());
    }

    @Test
    void produtoQueFicouOcultoContinuaComResumoNaEstante() throws Exception {
        guardar(tokenA, oculto);
        int empresa = catalogo().empresa("Dona do Oculto", "dona@seguranca.test");
        catalogo().vinculo(empresa, oculto, "FALSE");

        resumo(tokenA, oculto).andExpect(status().isOk()).andExpect(jsonPath("$.resumo").value(Matchers.nullValue()));
        composicao(tokenA, oculto).andExpect(status().isNotFound());
    }

    @Test
    void composicaoOrdenadaPorNomeComClassesEsemSubstanciaNaoResolvida() throws Exception {
        composicao(tokenA, limpaVidros)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].substanciaId").value(hidroxido))
                .andExpect(jsonPath("$[0].nome").value("Hidróxido de sódio"))
                .andExpect(jsonPath("$[0].casNumero").value("1310-73-2"))
                .andExpect(jsonPath("$[0].concentracaoMin").value(1.0))
                .andExpect(jsonPath("$[0].concentracaoMax").value(Matchers.nullValue()))
                .andExpect(jsonPath("$[0].classes", Matchers.contains("Corrosivo")))
                .andExpect(jsonPath("$[1].substanciaId").value(hipoclorito))
                .andExpect(jsonPath("$[1].concentracaoMin").value(5.0))
                .andExpect(jsonPath("$[1].concentracaoMax").value(6.0))
                .andExpect(jsonPath("$[1].unidadeConcentracao").value("%"))
                .andExpect(jsonPath("$[1].classes", Matchers.contains("Corrosivo", "Oxidante")));
    }

    @Test
    void composicaoVaziaSemFdsEnotFoundParaProdutoInexistente() throws Exception {
        composicao(tokenA, semFds).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        composicao(tokenA, 999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_encontrado"));
    }

    @Test
    void substanciasDaEstanteAgrupamPorSubstanciaComOsProdutosDoUsuario() throws Exception {
        guardar(tokenA, limpaVidros, aguaSanitaria, semFds, oculto);
        guardar(tokenB, semFds);
        catalogo().vinculo(catalogo().empresa("Dona", "dona2@seguranca.test"), oculto, "FALSE");

        mvc.perform(get("/api/v1/estante/substancias").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(3))
                .andExpect(jsonPath("$.itens[0].nome").value("Etanol"))
                .andExpect(jsonPath("$.itens[0].produtos[*].id", Matchers.contains(oculto)))
                .andExpect(jsonPath("$.itens[1].nome").value("Hidróxido de sódio"))
                .andExpect(jsonPath("$.itens[1].produtos[*].nome", Matchers.contains("Limpa Vidros")))
                .andExpect(jsonPath("$.itens[2].nome").value("Hipoclorito de sódio"))
                .andExpect(jsonPath("$.itens[2].classes", Matchers.contains("Corrosivo", "Oxidante")))
                .andExpect(jsonPath("$.itens[2].produtos[*].id", Matchers.containsInAnyOrder(limpaVidros, aguaSanitaria)));
        mvc.perform(get("/api/v1/estante/substancias").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));
    }

    @Test
    void detalheDaSubstanciaComSinonimosEClasses() throws Exception {
        substancia(tokenA, hipoclorito)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Hipoclorito de sódio"))
                .andExpect(jsonPath("$.casNumero").value("7681-52-9"))
                .andExpect(jsonPath("$.descricao").value("Alvejante e desinfetante"))
                .andExpect(jsonPath("$.sinonimos", Matchers.contains("Cloro ativo", "Água de Javel")))
                .andExpect(jsonPath("$.classes.length()").value(2))
                .andExpect(jsonPath("$.classes[0].nome").value("Corrosivo"))
                .andExpect(jsonPath("$.classes[0].descricao").value("Corrói tecidos e metais"));
        substancia(tokenA, 999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("substancia_nao_encontrada"));
    }

    @Test
    void compostosGeradosPeloTriggerDoBancoAparecemNaComposicao() throws Exception {
        Assumptions.assumeTrue(catalogo().postgres(), "o trigger trg_processar_fds_raw_json só existe no schema real");
        String raw = """
                {"secoes":{"03":{"compostos":[
                  {"nome":"Soda cáustica","cas_numero":"1310-73-2","concentracao_min":"1","concentracao_max":"2"},
                  {"nome":"Perfume sem cadastro","cas_numero":null,"concentracao_min":"0.1"}]}}}
                """;
        jdbc.update(
                "INSERT INTO fds (id_produto, versao, data_atualizacao, ativo, resumo_simplificado, raw_json)"
                        + " VALUES (?, 'v9', DATE '2025-05-05', TRUE, 'Resumo vindo do parser', CAST(? AS jsonb))",
                semFds, raw);

        composicao(tokenA, semFds)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].substanciaId").value(hidroxido))
                .andExpect(jsonPath("$[0].concentracaoMin").value(1.0))
                .andExpect(jsonPath("$[0].concentracaoMax").value(2.0))
                .andExpect(jsonPath("$[0].unidadeConcentracao").value(Matchers.nullValue()));
        guardar(tokenA, semFds);
        resumo(tokenA, semFds).andExpect(jsonPath("$.resumo").value("Resumo vindo do parser"));
    }

    @Test
    void exigeTokenDeUsuario() throws Exception {
        String[] rotas = {
            "/api/v1/estante/produtos/" + limpaVidros + "/resumo-simplificado",
            "/api/v1/estante/substancias",
            "/api/v1/produtos/" + limpaVidros + "/substancias",
            "/api/v1/substancias/" + hipoclorito
        };
        for (String rota : rotas) {
            mvc.perform(get(rota)).andExpect(status().isUnauthorized());
            mvc.perform(get(rota).header("Authorization", "Bearer " + tokenEmpresa)).andExpect(status().isForbidden());
        }
    }

    private TestCatalogo catalogo() {
        return new TestCatalogo(jdbc);
    }

    private ResultActions guardar(String token, int... produtoIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int id : produtoIds) {
            ids.append(ids.length() == 0 ? "" : ",").append(id);
        }
        return mvc.perform(post("/api/v1/estante/produtos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoIds\":[" + ids + "]}"));
    }

    private ResultActions resumo(String token, int produtoId) throws Exception {
        return mvc.perform(get("/api/v1/estante/produtos/" + produtoId + "/resumo-simplificado")
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions composicao(String token, int produtoId) throws Exception {
        return mvc.perform(get("/api/v1/produtos/" + produtoId + "/substancias")
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions substancia(String token, int id) throws Exception {
        return mvc.perform(get("/api/v1/substancias/" + id).header("Authorization", "Bearer " + token));
    }
}
