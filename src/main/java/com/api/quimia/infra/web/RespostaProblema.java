package com.api.quimia.infra.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Escreve, fora dos controllers (filtros de segurança e firewall), o mesmo `application/problem+json`
 * do {@link ProblemHandler}: `title`, `status`, `detail` com código estável e `instance`.
 */
public final class RespostaProblema {
    private static final int PRIMEIRO_CARACTERE_IMPRIMIVEL = 0x20;

    private RespostaProblema() {}

    public static void escrever(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + textoJson(detail)
                + "\",\"instance\":\"" + textoJson(request.getRequestURI()) + "\"}");
    }

    /** O caminho vem do cliente: aspas, barras invertidas e caracteres de controle são escapados. */    private static String textoJson(String texto) {
        StringBuilder escapado = new StringBuilder(texto.length());
        for (char caractere : texto.toCharArray()) {
            if (caractere == '"' || caractere == '\\') {
                escapado.append('\\').append(caractere);
            } else if (caractere < PRIMEIRO_CARACTERE_IMPRIMIVEL) {
                escapado.append(String.format("\\u%04x", (int) caractere));
            } else {
                escapado.append(caractere);
            }
        }
        return escapado.toString();
    }
}
