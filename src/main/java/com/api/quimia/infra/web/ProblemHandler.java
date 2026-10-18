package com.api.quimia.infra.web;

import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.catalog.internal.usecase.CatalogException;
import com.api.quimia.domain.insights.internal.usecase.InsightsException;
import com.api.quimia.domain.partner.internal.usecase.PartnerException;
import com.api.quimia.domain.recommendation.internal.usecase.RecommendationException;
import com.api.quimia.domain.safety.internal.usecase.SafetyException;
import com.api.quimia.domain.storage.internal.usecase.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ProblemHandler {
    /**
     * SQLSTATEs de texto que o banco não consegue representar (22021: byte inválido para a codificação, como o
     * NUL; 22P05: caractere sem tradução). Vêm do texto enviado pelo cliente. Os demais erros de dado (ex.:
     * 22001, texto maior que a coluna) podem nascer de valor gerado pela própria API e não mudam de tratamento.
     */
    private static final Set<String> SQLSTATES_TEXTO_INVALIDO = Set.of("22021", "22P05");

    @ExceptionHandler(AccountException.class)
    ProblemDetail account(AccountException error, HttpServletRequest request) {
        return problem(error.status(), codeTitle(error.code()), error.code(), request);
    }

    @ExceptionHandler(CatalogException.class)
    ProblemDetail catalog(CatalogException error, HttpServletRequest request) {
        return problem(error.status(), codeTitle(error.code()), error.code(), request);
    }

    @ExceptionHandler(SafetyException.class)
    ProblemDetail safety(SafetyException error, HttpServletRequest request) {
        return problem(error.status(), codeTitle(error.code()), error.code(), request);
    }

    @ExceptionHandler(StorageException.class)
    ProblemDetail storage(StorageException error, HttpServletRequest request) {
        return problem(error.status(), codeTitle(error.code()), error.code(), request);
    }

    @ExceptionHandler(RecommendationException.class)
    ProblemDetail recommendation(RecommendationException error, HttpServletRequest request) {
        return problem(error.status(), codeTitle(error.code()), error.code(), request);
    }

    @ExceptionHandler(PartnerException.class)
    ProblemDetail partner(PartnerException error, HttpServletRequest request) {
        return problem(error.status(), codeTitle(error.code()), error.code(), request);
    }

    @ExceptionHandler(InsightsException.class)
    ProblemDetail insights(InsightsException error, HttpServletRequest request) {
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

    /**
     * Corrida entre requisições simultâneas que viola um UNIQUE do banco (ex.: mesmo código de barras) é
     * conflito. Texto com caractere que o banco não aceita (ex.: byte NUL) é erro de entrada, não conflito.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException error, HttpServletRequest request) {
        if (textoRecusadoPeloBanco(error)) {
            return problem(400, "Bad Request", "valor_invalido", request);
        }
        return problem(409, "Conflict", "conflito", request);
    }

    /** Rota que não existe (com token válido; sem token o Spring Security responde 401 antes). */
    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail rotaNaoEncontrada(NoResourceFoundException error, HttpServletRequest request) {
        return problem(404, "Not Found", "rota_nao_encontrada", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> metodoNaoPermitido(
            HttpRequestMethodNotSupportedException error, HttpServletRequest request) {
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (error.getSupportedHttpMethods() != null) {
            resposta.allow(error.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        }
        return resposta.body(problem(405, "Method Not Allowed", "metodo_nao_permitido", request));
    }

    /**
     * Arquivo acima do limite do multipart (spring.servlet.multipart: 15 MB, o do PDF da FDS), recusado antes de
     * chegar ao controller. Imagem acima de 5 MB e abaixo desse limite responde `imagem_muito_grande` pelo caso de uso.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadGrande(MaxUploadSizeExceededException error, HttpServletRequest request) {
        return problem(413, "Payload Too Large", "arquivo_muito_grande", request);
    }

    /** JSON malformado, corpo ausente ou campo com tipo errado (ex.: objeto no lugar de texto, 1.9 como id). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpoInvalido(HttpMessageNotReadableException error, HttpServletRequest request) {
        return problem(400, "Bad Request", "corpo_invalido", request);
    }

    /** Parâmetro de query ou de caminho que não converte para o tipo esperado (ex.: size=abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException error, HttpServletRequest request) {
        return problem(400, "Bad Request", "parametro_invalido", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail midiaNaoSuportada(HttpMediaTypeNotSupportedException error, HttpServletRequest request) {
        return problem(415, "Unsupported Media Type", "tipo_midia_nao_suportado", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraint(ConstraintViolationException error, HttpServletRequest request) {
        return problem(400, "Validation failed", "validation", request);
    }

    private static boolean textoRecusadoPeloBanco(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && SQLSTATES_TEXTO_INVALIDO.contains(sql.getSQLState())) {
                return true;
            }
        }
        return false;
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
            case "not_found", "produto_nao_encontrado", "usuario_nao_encontrado", "empresa_nao_encontrada",
                    "produto_nao_esta_na_estante", "substancia_nao_encontrada" -> "Not Found";
            case "codigo_barras_invalido" -> "Unprocessable";
            case "paginacao_invalida", "tipo_invalido", "termo_muito_longo", "nome_invalido" -> "Bad Request";
            case "imagem_invalida" -> "Unsupported Media Type";
            case "imagem_muito_grande" -> "Payload Too Large";
            case "armazenamento_imagem_falhou" -> "Bad Gateway";
            case "armazenamento_imagem_indisponivel" -> "Service Unavailable";
            case "firebase_disabled" -> "Service Unavailable";
            case "invalid_transport", "invalid_recovery_code", "invalid_reset_token" -> "Bad Request";
            case "ordem_invalida", "ordenacao_invalida", "imagem_obrigatoria" -> "Bad Request";
            case "comodo_nao_encontrado", "produto_sem_comodo", "mistura_nao_encontrada" -> "Not Found";
            case "comodo_ja_existe", "produto_em_outro_comodo" -> "Conflict";
            case "comodo_produtos_insuficientes", "comodo_produtos_demais", "produto_fora_da_estante",
                    "produtos_iguais", "estado_invalido", "cep_invalido", "localizacao_necessaria",
                    "fds_obrigatoria", "fds_invalida" -> "Unprocessable";
            case "pdf_muito_grande" -> "Payload Too Large";
            case "pdf_invalido" -> "Unsupported Media Type";
            case "limite_excedido" -> "Too Many Requests";
            case "agente_falhou" -> "Bad Gateway";
            case "agente_indisponivel" -> "Service Unavailable";
            case "agente_timeout" -> "Gateway Timeout";
            case "extrator_falhou" -> "Bad Gateway";
            case "extrator_indisponivel" -> "Service Unavailable";
            default -> "Error";
        };
    }
}
