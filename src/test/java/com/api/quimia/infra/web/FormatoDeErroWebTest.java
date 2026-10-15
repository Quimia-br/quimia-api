package com.api.quimia.infra.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Erros que nascem fora dos controllers (Spring Security, roteamento e firewall) seguem o mesmo contrato
 * dos erros de negócio: `application/problem+json` em UTF-8, com `detail` estável e `instance`.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class FormatoDeErroWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenUsuario;
    private String tokenEmpresa;

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@formato.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@formato.test'");
        tokenUsuario = TestLogins.usuario(mvc, "u@formato.test");
        tokenEmpresa = TestLogins.empresa(mvc, "e@formato.test", "30000011000132");
    }

    private static void problema(ResultActions resposta, String detail, String instance) throws Exception {
        resposta.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Content-Type", Matchers.anyOf(
                        Matchers.not(Matchers.containsString("charset")), Matchers.containsString("UTF-8"))))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(instance))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    void semTokenDevolve401NoFormatoDoContrato() throws Exception {
        ResultActions resposta = mvc.perform(get("/api/v1/usuarios/me")).andExpect(status().isUnauthorized());
        problema(resposta, "nao_autenticado", "/api/v1/usuarios/me");
        resposta.andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void titularErradoDevolve403NoFormatoDoContrato() throws Exception {
        ResultActions resposta = mvc.perform(get("/api/v1/produtos").header("Authorization", "Bearer " + tokenEmpresa))
                .andExpect(status().isForbidden());
        problema(resposta, "acesso_negado", "/api/v1/produtos");
        resposta.andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void rotaInexistenteDevolve404NoFormatoDoContrato() throws Exception {
        ResultActions resposta = mvc.perform(get("/api/v1/rota-inexistente").header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(status().isNotFound());
        problema(resposta, "rota_nao_encontrada", "/api/v1/rota-inexistente");
    }

    @Test
    void verboNaoSuportadoDevolve405ComAllow() throws Exception {
        ResultActions resposta = mvc.perform(post("/api/v1/produtos").header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", Matchers.containsString("GET")));
        problema(resposta, "metodo_nao_permitido", "/api/v1/produtos");
    }

    @Test
    void requisicaoRecusadaPeloFirewallDevolve400NoFormatoDoContrato() throws Exception {
        ResultActions resposta = mvc.perform(get("/api/v1/produtos;x=1").header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(status().isBadRequest());
        problema(resposta, "requisicao_rejeitada", "/api/v1/produtos;x=1");
        resposta.andExpect(jsonPath("$.status").value(400));
    }
}
