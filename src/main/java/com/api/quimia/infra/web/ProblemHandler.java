package com.api.quimia.infra.web;

import com.api.quimia.domain.account.internal.usecase.AccountException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemHandler {
    @ExceptionHandler(AccountException.class)
    ProblemDetail account(AccountException error, HttpServletRequest request) {
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
            case "invalid_credentials", "invalid_refresh", "invalid_token" -> "Unauthorized";
            case "email_not_verified", "blocked" -> "Forbidden";
            case "email_in_use" -> "Conflict";
            case "weak_password", "underage" -> "Unprocessable";
            case "invalid_transport" -> "Bad Request";
            default -> "Error";
        };
    }
}
