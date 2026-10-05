package com.api.quimia.infra.web;

import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.catalog.internal.usecase.CatalogException;
import com.api.quimia.domain.storage.internal.usecase.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ProblemHandler {
    @ExceptionHandler(AccountException.class)
    ProblemDetail account(AccountException error, HttpServletRequest request) {
        return problem(error.status(), codeTitle(error.code()), error.code(), request);
    }

    @ExceptionHandler(CatalogException.class)
    ProblemDetail catalog(CatalogException error, HttpServletRequest request) {
        return problem(error.status(), codeTitle(error.code()), error.code(), request);
    }

    @ExceptionHandler(StorageException.class)
    ProblemDetail storage(StorageException error, HttpServletRequest request) {
        return problem(error.status(), codeTitle(error.code()), error.code(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        List<String> errors = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .toList();
        ProblemDetail detail = problem(400, "Validation failed", "validation", request);
        detail.setProperty("errors", errors);
        return detail;
    }

    /** Corrida entre requisições simultâneas que viola um UNIQUE do banco (ex.: mesmo código de barras). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException error, HttpServletRequest request) {
        return problem(409, "Conflict", "conflito", request);
    }

    /** Imagem acima do limite do multipart (spring.servlet.multipart), recusada antes de chegar ao controller. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadGrande(MaxUploadSizeExceededException error, HttpServletRequest request) {
        return problem(413, "Payload Too Large", "imagem_muito_grande", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraint(ConstraintViolationException error, HttpServletRequest request) {
        return problem(400, "Validation failed", "validation", request);
    }

    private static ProblemDetail problem(int status, String title, String code, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), code);
        detail.setTitle(title);
        detail.setInstance(URI.create(request.getRequestURI()));
        return detail;
    }

    private static String codeTitle(String code) {
        return switch (code) {
            case "invalid_credentials", "invalid_refresh", "invalid_token", "invalid_firebase_token" -> "Unauthorized";
            case "blocked", "inactive", "email_not_verified" -> "Forbidden";
            case "email_in_use", "cnpj_in_use", "produto_ja_cadastrado", "produto_compartilhado", "cod_barras_em_uso" -> "Conflict";
            case "empresa_inativa" -> "Forbidden";
            case "weak_password", "password_too_long", "underage", "invalid_cnpj", "invalid_cep" -> "Unprocessable";
            case "not_found", "produto_nao_encontrado", "usuario_nao_encontrado", "empresa_nao_encontrada" -> "Not Found";
            case "codigo_barras_invalido" -> "Unprocessable";
            case "paginacao_invalida", "tipo_invalido", "termo_muito_longo", "nome_invalido" -> "Bad Request";
            case "imagem_invalida" -> "Unsupported Media Type";
            case "imagem_muito_grande" -> "Payload Too Large";
            case "armazenamento_imagem_falhou" -> "Bad Gateway";
            case "armazenamento_imagem_indisponivel" -> "Service Unavailable";
            case "firebase_disabled" -> "Service Unavailable";
            case "invalid_transport", "invalid_recovery_code", "invalid_reset_token" -> "Bad Request";
            default -> "Error";
        };
    }
}
