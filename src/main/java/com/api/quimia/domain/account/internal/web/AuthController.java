package com.api.quimia.domain.account.internal.web;

import com.api.quimia.domain.account.NivelAcesso;
import com.api.quimia.domain.account.dto.UserSummary;
import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.dto.LoginResponse;
import com.api.quimia.domain.account.internal.dto.MobileLoginResponse;
import com.api.quimia.domain.account.internal.dto.RefreshRequest;
import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.dto.RegisterResponse;
import com.api.quimia.domain.account.internal.dto.ResendVerificationRequest;
import com.api.quimia.domain.account.internal.dto.VerifyEmailRequest;
import com.api.quimia.domain.account.internal.usecase.AutenticarUseCase;
import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.account.internal.usecase.IssuedSession;
import com.api.quimia.domain.account.internal.usecase.EncerrarSessaoUseCase;
import com.api.quimia.domain.account.internal.usecase.ReenviarVerificacaoUseCase;
import com.api.quimia.domain.account.internal.usecase.RegistrarUseCase;
import com.api.quimia.domain.account.internal.usecase.RenovarUseCase;
import com.api.quimia.domain.account.internal.usecase.VerificarEmailUseCase;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String CSRF_COOKIE_NAME = "quimia_csrf";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RegistrarUseCase registrar;
    private final VerificarEmailUseCase verificar;
    private final ReenviarVerificacaoUseCase reenviar;
    private final AutenticarUseCase autenticar;
    private final RenovarUseCase renovar;
    private final EncerrarSessaoUseCase encerrar;

    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(
            RegistrarUseCase registrar,
            VerificarEmailUseCase verificar,
            ReenviarVerificacaoUseCase reenviar,
            AutenticarUseCase autenticar,
            RenovarUseCase renovar,
            EncerrarSessaoUseCase encerrar,
            @Value("${app.auth.cookie-name:quimia_rt}") String cookieName,
            @Value("${app.auth.cookie-secure:true}") boolean cookieSecure,
            @Value("${app.auth.cookie-samesite:Lax}") String cookieSameSite) {
        this.registrar = registrar;
        this.verificar = verificar;
        this.reenviar = reenviar;
        this.autenticar = autenticar;
        this.renovar = renovar;
        this.encerrar = encerrar;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        UserSummary user = registrar.execute(request);
        return new RegisterResponse(user.id(), user.nome(), user.email(), NivelAcesso.USUARIO);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyEmailRequest request) {
        verificar.execute(request.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody ResendVerificationRequest request) {
        reenviar.execute(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        IssuedSession session = autenticar.execute(request);
        addWebCookies(response, session.refreshToken());
        return session.response();
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(
            @RequestBody(required = false) RefreshRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        rejectBodyTransport(body);
        IssuedSession session = renovar.execute(cookieValue(request, cookieName));
        addWebCookies(response, session.refreshToken());
        return session.response();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestBody(required = false) RefreshRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        rejectBodyTransport(body);
        encerrar.execute(cookieValue(request, cookieName));
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearCsrfCookie().toString());
    }

    @PostMapping("/mobile/login")
    public MobileLoginResponse mobileLogin(@Valid @RequestBody LoginRequest request) {
        return mobileResponse(autenticar.execute(request));
    }

    @PostMapping("/mobile/refresh")
    public MobileLoginResponse mobileRefresh(
            @RequestBody(required = false) RefreshRequest body,
            HttpServletRequest request) {
        rejectCookieTransport(request);
        return mobileResponse(renovar.execute(body == null ? null : body.refreshToken()));
    }

    @PostMapping("/mobile/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mobileLogout(
            @RequestBody(required = false) RefreshRequest body,
            HttpServletRequest request) {
        rejectCookieTransport(request);
        encerrar.execute(body == null ? null : body.refreshToken());
    }

    private static MobileLoginResponse mobileResponse(IssuedSession session) {
        LoginResponse response = session.response();
        return new MobileLoginResponse(
                response.accessToken(),
                response.tokenType(),
                response.expiresIn(),
                session.refreshToken(),
                response.user());
    }

    private static void rejectBodyTransport(RefreshRequest body) {
        if (body != null) {
            throw new AccountException("invalid_transport", 400);
        }
    }

    private void rejectCookieTransport(HttpServletRequest request) {
        if (cookieValue(request, cookieName) != null) {
            throw new AccountException("invalid_transport", 400);
        }
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie item : request.getCookies()) {
                if (name.equals(item.getName())) {
                    return item.getValue();
                }
            }
        }
        return null;
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(30L * 24 * 60 * 60)
                .build();
    }

    private ResponseCookie csrfCookie(String value) {
        return ResponseCookie.from(CSRF_COOKIE_NAME, value)
                .httpOnly(false)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(30L * 24 * 60 * 60)
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    private ResponseCookie clearCsrfCookie() {
        return ResponseCookie.from(CSRF_COOKIE_NAME, "")
                .httpOnly(false)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
    }

    private void addWebCookies(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(newCsrfToken()).toString());
    }

    private static String newCsrfToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
