package com.api.quimia.infra.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.infra.web.internal.web.RotasDeErroParaTeste;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Erros que antes caíam no JSON padrão do Spring Boot (`timestamp`, `error`, `path`) seguem o contrato:
 * parâmetro obrigatório ausente, `Accept` sem formato possível e exceção não prevista (500 sem detalhe interno).
 * Vale para o que acontece dentro do MVC; erro lançado num filtro ainda passa pelo `/error` do Spring Boot.
 * As rotas de {@link RotasDeErroParaTeste} entram pelo component scan, como qualquer controller.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class ErrosNaoPrevistosWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@erros.test'");
        token = TestLogins.usuario(mvc, "u@erros.test");
    }

    @Test
    void parametroObrigatorioAusenteDa400NoContrato() throws Exception {
        mvc.perform(get("/api/v1/teste-erros/obrigatorio").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("parametro_obrigatorio"))
                .andExpect(jsonPath("$.instance").value("/api/v1/teste-erros/obrigatorio"));
    }

    @Test
    void acceptSemFormatoPossivelDa406NoContrato() throws Exception {
        mvc.perform(get("/api/v1/teste-erros/json").header("Authorization", "Bearer " + token).accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("formato_nao_aceito"));
    }

    @Test
    void erroDoSpringComStatusProprioMantemOStatus() throws Exception {
        mvc.perform(get("/api/v1/teste-erros/conflito").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("requisicao_invalida"));
    }

    /** O tratador de 500 não pode engolir um 403: a exceção volta para o Spring Security responder. */
    @Test
    void acessoNegadoDentroDoControllerContinua403() throws Exception {
        mvc.perform(get("/api/v1/teste-erros/acesso-negado").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("acesso_negado"));
    }

    @Test
    void excecaoNaoPrevistaDa500SemVazarDetalhe() throws Exception {
        MvcResult resposta = mvc.perform(get("/api/v1/teste-erros/falha").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("erro_interno"))
                .andExpect(jsonPath("$.instance").value("/api/v1/teste-erros/falha"))
                .andReturn();

        assertThat(resposta.getResponse().getContentAsString()).doesNotContain(RotasDeErroParaTeste.SEGREDO, "IllegalStateException", "trace");
    }

    @Test
    void semTokenContinua401AntesDoTratadorDe500() throws Exception {
        mvc.perform(get("/api/v1/teste-erros/falha")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("nao_autenticado"));
    }
}
