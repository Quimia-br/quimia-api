package com.api.quimia.infra.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** O caminho vem do cliente e entra no corpo: o JSON continua válido e devolve o caminho intacto. */
class RespostaProblemaTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void escapaAspasBarraInvertidaEControleNoInstance() throws Exception {
        String caminho = "/api/v1/a\"b\\c\u0001d";
        MockHttpServletResponse response = new MockHttpServletResponse();

        RespostaProblema.escrever(new MockHttpServletRequest("GET", caminho), response, HttpStatus.BAD_REQUEST,
                "requisicao_rejeitada");

        JsonNode corpo = json.readTree(response.getContentAsString());
        assertThat(corpo.get("instance").asText()).isEqualTo(caminho);
        assertThat(corpo.get("detail").asText()).isEqualTo("requisicao_rejeitada");
        assertThat(corpo.get("status").asInt()).isEqualTo(400);
        assertThat(response.getContentType()).contains("application/problem+json").contains("UTF-8");
    }
}
