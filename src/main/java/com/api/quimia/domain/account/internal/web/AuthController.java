package com.api.quimia.domain.account.internal.web;

import com.api.quimia.domain.account.NivelAcesso;
import com.api.quimia.domain.account.dto.UserSummary;
import com.api.quimia.domain.account.internal.dto.FirebaseLoginRequest;
import com.api.quimia.domain.account.internal.dto.ForgotPasswordRequest;
import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.dto.MobileLoginResponse;
import com.api.quimia.domain.account.internal.dto.RecoveryChallengeResponse;
import com.api.quimia.domain.account.internal.dto.RecoveryCodeRequest;
import com.api.quimia.domain.account.internal.dto.RecoveryGrantResponse;
import com.api.quimia.domain.account.internal.dto.RefreshRequest;
import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.dto.RegisterResponse;
import com.api.quimia.domain.account.internal.dto.ResetPasswordRequest;
import com.api.quimia.domain.account.internal.usecase.AutenticarFirebaseUseCase;
import com.api.quimia.domain.account.internal.usecase.AutenticarUseCase;
import com.api.quimia.domain.account.internal.usecase.PasswordRecoveryUseCase;
import com.api.quimia.domain.account.internal.usecase.RegistrarUseCase;
import com.api.quimia.domain.account.internal.usecase.RenovarUseCase;
import com.api.quimia.domain.account.internal.usecase.SessionIssuer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Autenticação do usuário consumidor (app mobile). Tokens trafegam no corpo. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String TOKEN_TYPE = "Bearer";

    private final RegistrarUseCase registrar;
    private final PasswordRecoveryUseCase recovery;
    private final AutenticarUseCase autenticar;
    private final AutenticarFirebaseUseCase firebase;
    private final RenovarUseCase renovar;

    public AuthController(
            RegistrarUseCase registrar,
            PasswordRecoveryUseCase recovery,
            AutenticarUseCase autenticar,
            AutenticarFirebaseUseCase firebase,
            RenovarUseCase renovar) {
        this.registrar = registrar;
        this.recovery = recovery;
        this.autenticar = autenticar;
        this.firebase = firebase;
        this.renovar = renovar;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        UserSummary user = registrar.execute(request);
        return new RegisterResponse(user.id(), user.nome(), user.email(), NivelAcesso.USUARIO);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RecoveryChallengeResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return recovery.requestCode(request.email());
    }

    @PostMapping("/verify-recovery-code")
    public RecoveryGrantResponse verifyRecoveryCode(@Valid @RequestBody RecoveryCodeRequest request) {
        return recovery.verifyCode(request.challengeToken(), request.email(), request.code());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recovery.resetPassword(request.resetToken(), request.novaSenha());
    }

    @PostMapping("/mobile/login")
    public MobileLoginResponse mobileLogin(@Valid @RequestBody LoginRequest request) {
        return response(autenticar.execute(request));
    }

    @PostMapping("/firebase")
    public MobileLoginResponse firebaseLogin(@Valid @RequestBody FirebaseLoginRequest request) {
        return response(firebase.execute(request.idToken()));
    }

    @PostMapping("/mobile/refresh")
    public MobileLoginResponse mobileRefresh(@RequestBody(required = false) RefreshRequest body) {
        return response(renovar.renewUsuario(body == null ? null : body.refreshToken()));
    }

    /** Sessões não têm estado no servidor: o app descarta os tokens. Mantido para o contrato do cliente. */
    @PostMapping("/mobile/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mobileLogout() {}

    private static MobileLoginResponse response(SessionIssuer.UsuarioSession session) {
        SessionIssuer.SessionTokens tokens = session.tokens();
        return new MobileLoginResponse(
                tokens.accessToken(), TOKEN_TYPE, tokens.expiresIn(), tokens.refreshToken(), session.user());
    }
}
