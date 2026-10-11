package com.api.quimia.infra.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

/** Texto que o banco não consegue representar (22021, 22P05) não é conflito; violação de restrição (23xxx) é. */
class ProblemHandlerIntegridadeTest {
    private final ProblemHandler handler = new ProblemHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/empresas/me/produtos");

    private static DataIntegrityViolationException comSqlState(String sqlState) {
        return new DataIntegrityViolationException("falha", new RuntimeException(new SQLException("banco", sqlState)));
    }

    @Test
    void byteInvalidoParaACodificacaoEhValorInvalido() {
        ProblemDetail problema = handler.integridade(comSqlState("22021"), request);

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getDetail()).isEqualTo("valor_invalido");
    }

    @Test
    void caractereSemTraducaoEhValorInvalido() {
        assertThat(handler.integridade(comSqlState("22P05"), request).getStatus()).isEqualTo(400);
    }

    /** Pode nascer de valor gerado pela API (ex.: URL de imagem longa): não é culpa do cliente, não muda. */
    @Test
    void textoMaiorQueAColunaContinuaComoAntes() {
        assertThat(handler.integridade(comSqlState("22001"), request).getStatus()).isEqualTo(409);
    }

    @Test
    void violacaoDeUnicidadeContinuaConflito() {
        ProblemDetail problema = handler.integridade(comSqlState("23505"), request);

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getDetail()).isEqualTo("conflito");
    }

    @Test
    void semSqlStateContinuaConflito() {
        ProblemDetail problema = handler.integridade(new DataIntegrityViolationException("falha"), request);

        assertThat(problema.getStatus()).isEqualTo(409);
    }
}
