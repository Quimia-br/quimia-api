package com.api.quimia.domain.storage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** GET /estante/produtos/{id}/primeiros-socorros ("Como se proteger?"). */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class PrimeirosSocorrosWebTest {
    private static final String SECAO_04 = """
            {"status":"processado","secoes":{"04":{"encontrada":true,
              "primeiros_socorros":[
                {"rota_exposicao":"olhos","descricao":"Lavar com água por 15 minutos.","atencao_medica_imediata":true},
                {"rota_exposicao":"inalacao","descricao":"Levar para local ventilado.","sintomas":"Tontura."}
              ],
              "orientacoes_gerais":[{"descricao":"Mostrar esta ficha ao médico."}],
              "notas_ao_medico":[]}}}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestCatalogo catalogo;
    private String tokenA;
    private String tokenB;
    private UUID usuarioA;
    private int marca;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@socorros.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@socorros.test'");
        tokenA = TestLogins.usuario(mvc, "a@socorros.test");
        tokenB = TestLogins.usuario(mvc, "b@socorros.test");
        usuarioA = catalogo.usuarioId("a@socorros.test");
        marca = catalogo.marca("Marca", null);
    }

    private int produtoNaEstante(String nome) {
        int produto = catalogo.produto(nome, marca, "limpeza_geral", null, null);
        catalogo.guardar(usuarioA, produto);
        return produto;
    }

    private ResultActions consultar(String token, Object produto) throws Exception {
        return mvc.perform(get("/api/v1/estante/produtos/" + produto + "/primeiros-socorros")
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void devolveASecao4DaFdsAtivaMaisRecenteNaOrdemDasRotas() throws Exception {
        int produto = produtoNaEstante("Alvejante");
        catalogo.fdsComConteudo(produto, "1", LocalDate.of(2025, 1, 1), true,
                "{\"secoes\":{\"04\":{\"encontrada\":true,\"primeiros_socorros\":"
                        + "[{\"rota_exposicao\":\"pele\",\"descricao\":\"Versão antiga\"}]}}}");
        catalogo.fdsComConteudo(produto, "3", LocalDate.of(2027, 1, 1), false,
                "{\"secoes\":{\"04\":{\"encontrada\":true,\"primeiros_socorros\":"
                        + "[{\"rota_exposicao\":\"pele\",\"descricao\":\"FDS inativa\"}]}}}");
        catalogo.fdsComConteudo(produto, "2", LocalDate.of(2026, 1, 1), true, SECAO_04);

        consultar(tokenA, produto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtoId").value(produto))
                .andExpect(jsonPath("$.encontrada").value(true))
                .andExpect(jsonPath("$.versaoFds").value("2"))
                .andExpect(jsonPath("$.dataAtualizacaoFds").value("2026-01-01"))
                .andExpect(jsonPath("$.porExposicao.length()").value(2))
                .andExpect(jsonPath("$.porExposicao[0].rotaExposicao").value("inalacao"))
                .andExpect(jsonPath("$.porExposicao[0].sintomas").value("Tontura."))
                .andExpect(jsonPath("$.porExposicao[0].atencaoMedicaImediata").value(false))
                .andExpect(jsonPath("$.porExposicao[1].rotaExposicao").value("olhos"))
                .andExpect(jsonPath("$.porExposicao[1].atencaoMedicaImediata").value(true))
                .andExpect(jsonPath("$.orientacoesGerais[0]").value("Mostrar esta ficha ao médico."))
                .andExpect(jsonPath("$.notasAoMedico.length()").value(0));
    }

    @Test
    void semFdsOuSemSecaoOuComJsonForaDoContratoVemNaoEncontradaSemErro() throws Exception {
        int semFds = produtoNaEstante("Sem FDS");
        int semSecao = produtoNaEstante("Sem seção 4");
        catalogo.fdsComConteudo(semSecao, "1", LocalDate.of(2026, 1, 1), true, "{\"secoes\":{\"03\":{\"compostos\":[]}}}");
        int foraDoContrato = produtoNaEstante("Fora do contrato");
        catalogo.fdsComConteudo(foraDoContrato, "1", LocalDate.of(2026, 1, 1), true,
                "{\"secoes\":{\"04\":{\"encontrada\":\"sim\",\"primeiros_socorros\":\"lavar\"}}}");
        int fdsSemJson = produtoNaEstante("FDS sem JSON");
        if (!catalogo.postgres()) {
            catalogo.fds(fdsSemJson, "1", LocalDate.of(2026, 1, 1), true, null);
        }

        for (int produto : new int[] {semFds, semSecao, foraDoContrato, fdsSemJson}) {
            consultar(tokenA, produto)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.encontrada").value(false))
                    .andExpect(jsonPath("$.porExposicao.length()").value(0))
                    .andExpect(jsonPath("$.orientacoesGerais.length()").value(0));
        }
        consultar(tokenA, semFds).andExpect(jsonPath("$.versaoFds").doesNotExist());
    }

    @Test
    void produtoForaDaEstanteDeOutroUsuarioOuInexistenteDa404() throws Exception {
        int produto = produtoNaEstante("Alvejante");
        catalogo.fdsComConteudo(produto, "1", LocalDate.of(2026, 1, 1), true, SECAO_04);

        for (Object id : new Object[] {999999, -1, 0, Integer.MAX_VALUE}) {
            consultar(tokenA, id)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("produto_nao_esta_na_estante"));
        }
        consultar(tokenB, produto)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_esta_na_estante"));
    }

    @Test
    void idInvalidoDa400() throws Exception {
        for (String id : new String[] {"abc", "2147483648", "1.5"}) {
            consultar(tokenA, id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("parametro_invalido"));
        }
    }

    @Test
    void exigeUsuarioDoApp() throws Exception {
        int produto = produtoNaEstante("Alvejante");

        mvc.perform(get("/api/v1/estante/produtos/" + produto + "/primeiros-socorros"))
                .andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@socorros.test", "30000017000100");
        consultar(empresa, produto).andExpect(status().isForbidden());
    }
}
