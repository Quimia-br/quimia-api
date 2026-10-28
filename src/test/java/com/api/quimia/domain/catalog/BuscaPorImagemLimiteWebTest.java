package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotulo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Limite de `POST /produtos/busca-por-imagem` por usuário, reduzido a 2 por hora neste teste. */
@SpringBootTest(classes = com.api.quimia.Application.class, properties = "quimia.agente.buscas-por-imagem-por-hora=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(BuscaPorImagemLimiteWebTest.AgenteFalso.class)
class BuscaPorImagemLimiteWebTest {
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'
    };

    @TestConfiguration
    static class AgenteFalso {
        @Bean
        @Primary
        FakeLeitorDeRotulo fakeLeitorDeRotulo() {
            return new FakeLeitorDeRotulo();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LeitorDeRotulo leitorInjetado;

    private FakeLeitorDeRotulo agente;

    @BeforeEach
    void seed() {
        agente = (FakeLeitorDeRotulo) leitorInjetado;
        agente.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@rotulolimite.test'");
    }

    private ResultActions escanear(String token, byte[] conteudo) throws Exception {
        return mvc.perform(multipart("/api/v1/produtos/busca-por-imagem")
                .file(new MockMultipartFile("imagem", "rotulo.png", "image/png", conteudo))
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void acimaDoLimiteRespondeLimiteExcedidoSemChamarOAgente() throws Exception {
        String token = TestLogins.usuario(mvc, "a@rotulolimite.test");
        escanear(token, PNG).andExpect(status().isOk());
        escanear(token, PNG).andExpect(status().isOk());

        escanear(token, PNG)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("limite_excedido"));

        assertThat(agente.chamadas()).isEqualTo(2);
    }

    @Test
    void imagemRecusadaNaoContaEOutroUsuarioTemSeuLimite() throws Exception {
        String token = TestLogins.usuario(mvc, "b@rotulolimite.test");
        for (int i = 0; i < 3; i++) {
            escanear(token, new byte[0]).andExpect(status().isBadRequest());
        }
        escanear(token, PNG).andExpect(status().isOk());
        escanear(token, PNG).andExpect(status().isOk());
        escanear(token, PNG).andExpect(status().isTooManyRequests());

        String outro = TestLogins.usuario(mvc, "c@rotulolimite.test");
        escanear(outro, PNG).andExpect(status().isOk());
    }
}
