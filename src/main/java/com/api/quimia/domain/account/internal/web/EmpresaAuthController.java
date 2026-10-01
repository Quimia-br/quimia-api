package com.api.quimia.domain.account.internal.web;

import com.api.quimia.domain.account.dto.EmpresaSummary;
import com.api.quimia.domain.account.internal.dto.EmpresaLoginResponse;
import com.api.quimia.domain.account.internal.dto.EmpresaRegisterRequest;
import com.api.quimia.domain.account.internal.dto.ForgotPasswordRequest;
import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.dto.ResetPasswordRequest;
import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.account.internal.usecase.AutenticarEmpresaUseCase;
import com.api.quimia.domain.account.internal.usecase.EmpresaPasswordRecoveryUseCase;
import com.api.quimia.domain.account.internal.usecase.RegistrarEmpresaUseCase;
import com.api.quimia.domain.account.internal.usecase.RenovarUseCase;
import com.api.quimia.domain.account.internal.usecase.SessionIssuer;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Autenticação do portal web da empresa: refresh em cookie HttpOnly e CSRF por double-submit. */
@RestController
@RequestMapping(EmpresaAuthController.BASE_PATH)
public class EmpresaAuthController {
    public static final String BASE_PATH = "/api/v1/empresas/auth";
    public static final String CSRF_COOKIE_NAME = "quimia_csrf";
    private static final String TOKEN_TYPE = "Bearer";
    private static final int CSRF_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RegistrarEmpresaUseCase registrar;
    private final AutenticarEmpresaUseCase autenticar;
    private final RenovarUseCase renovar;
    private final EmpresaPasswordRecoveryUseCase recovery;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final Duration cookieMaxAge;

    public EmpresaAuthController(
            RegistrarEmpresaUseCase registrar,
            AutenticarEmpresaUseCase autenticar,
            RenovarUseCase renovar,
            EmpresaPasswordRecoveryUseCase recovery,
            @Value("${app.auth.cookie-name:quimia_rt}") String cookieName,
            @Value("${app.auth.cookie-secure:true}") boolean cookieSecure,
            @Value("${app.auth.cookie-samesite:Lax}") String cookieSameSite,
            @Value("${app.auth.refresh-ttl-days:30}") long refreshTtlDays) {
        this.registrar = registrar;
        this.autenticar = autenticar;
        this.renovar = renovar;
        this.recovery = recovery;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieMaxAge = Duration.ofDays(refreshTtlDays);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public EmpresaSummary register(@Valid @RequestBody EmpresaRegisterRequest request) {
        return registrar.execute(request);
    }

    @PostMapping("/login")
    public EmpresaLoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return respond(autenticar.execute(request), response);
    }

    @PostMapping("/refresh")
    public EmpresaLoginResponse refresh(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response) {
        rejectBodyTransport(body);
        return respond(renovar.renewEmpresa(cookieValue(request, cookieName)), response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookieName, "", true, BASE_PATH, Duration.ZERO));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE_NAME, "", false, "/", Duration.ZERO));
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        recovery.requestLink(request.email());
        return Map.of();
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recovery.resetPassword(request.resetToken(), request.novaSenha());
    }

    private EmpresaLoginResponse respond(SessionIssuer.EmpresaSession session, HttpServletResponse response) {
        SessionIssuer.SessionTokens tokens = session.tokens();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookieName, tokens.refreshToken(), true, BASE_PATH, cookieMaxAge));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE_NAME, newCsrfToken(), false, "/", cookieMaxAge));
        return new EmpresaLoginResponse(tokens.accessToken(), TOKEN_TYPE, tokens.expiresIn(), session.empresa());
    }

    /** O refresh web só é aceito pelo cookie; enviá-lo no corpo indica uso indevido do cliente. */
    private static void rejectBodyTransport(Map<String, Object> body) {
        if (body != null) {
            throw new AccountException("invalid_transport", 400);
        }
    }

    private String cookie(String name, String value, boolean httpOnly, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie item : request.getCookies()) {
            if (name.equals(item.getName())) {
                return item.getValue();
            }
        }
        return null;
    }

    private static String newCsrfToken() {
        byte[] bytes = new byte[CSRF_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
