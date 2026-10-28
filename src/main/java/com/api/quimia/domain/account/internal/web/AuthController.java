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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Tag(name = "Usuário: conta")
    @Operation(
            summary = "Cadastrar usuário do app",
            description = "Cria a conta do consumidor. A senha precisa de 8 caracteres ou mais, com letra e "
                    + "número, e até 72 bytes; a data de nascimento exige 16 anos ou mais. Não faz login: em "
                    + "seguida chame `/auth/mobile/login`.")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Conta criada.")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado: `email_in_use`.")
    @ApiResponse(
            responseCode = "422",
            description = "Senha fraca, senha longa demais ou menor de idade: `weak_password`, "
                    + "`password_too_long`, `underage`.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        UserSummary user = registrar.execute(request);
        return new RegisterResponse(user.id(), user.nome(), user.email(), NivelAcesso.USUARIO);
    }

    @Tag(name = "Usuário: recuperação de senha")
    @Operation(
            summary = "Pedir código de recuperação de senha",
            description = "Envia um código de 4 dígitos por e-mail (válido por 15 minutos) e devolve o "
                    + "`challengeToken` da tentativa. Responde 202 mesmo para e-mail sem conta, para não "
                    + "revelar quem está cadastrado. Pedidos repetidos são limitados por e-mail.")
    @SecurityRequirements
    @ApiResponse(responseCode = "202", description = "Pedido aceito; o código vai por e-mail se a conta existir.")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RecoveryChallengeResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return recovery.requestCode(request.email());
    }

    @Tag(name = "Usuário: recuperação de senha")
    @Operation(
            summary = "Conferir o código de recuperação",
            description = "Troca o código de 4 dígitos (com o `challengeToken` e o e-mail) por um `resetToken` de "
                    + "uso único. Tentativas erradas demais bloqueiam a recuperação por um tempo.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Código certo; devolve o `resetToken`.")
    @ApiResponse(
            responseCode = "400",
            description = "Código errado, expirado, já usado ou tentativas esgotadas: `invalid_recovery_code`.")
    @PostMapping("/verify-recovery-code")
    public RecoveryGrantResponse verifyRecoveryCode(@Valid @RequestBody RecoveryCodeRequest request) {
        return recovery.verifyCode(request.challengeToken(), request.email(), request.code());
    }

    @Tag(name = "Usuário: recuperação de senha")
    @Operation(
            summary = "Trocar a senha com o resetToken",
            description = "Grava a nova senha e invalida as sessões abertas (os refresh tokens anteriores deixam "
                    + "de valer).")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Senha trocada.")
    @ApiResponse(
            responseCode = "400",
            description = "Token de troca inválido, expirado ou já usado: `invalid_reset_token`.")
    @ApiResponse(
            responseCode = "422",
            description = "Senha nova fora das regras: `weak_password`, `password_too_long`.")
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recovery.resetPassword(request.resetToken(), request.novaSenha());
    }

    @Tag(name = "Usuário: conta")
    @Operation(
            summary = "Entrar no app com e-mail e senha",
            description = "Devolve o access token (15 minutos) e o refresh token no corpo. Cinco senhas erradas "
                    + "seguidas bloqueiam o login por 15 minutos.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Login feito.")
    @ApiResponse(responseCode = "401", description = "E-mail ou senha errados: `invalid_credentials`.")
    @ApiResponse(responseCode = "423", description = "Login bloqueado por tentativas erradas: `blocked`.")
    @PostMapping("/mobile/login")
    public MobileLoginResponse mobileLogin(@Valid @RequestBody LoginRequest request) {
        return response(autenticar.execute(request));
    }

    @Tag(name = "Usuário: conta")
    @Operation(
            summary = "Entrar no app com Google (Firebase)",
            description = "Recebe o ID token do Firebase. Cria a conta no primeiro acesso, com a foto do Google "
                    + "quando o usuário ainda não tem foto.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Login feito.")
    @ApiResponse(
            responseCode = "401",
            description = "ID token do Firebase inválido ou expirado: `invalid_firebase_token`.")
    @ApiResponse(responseCode = "403", description = "E-mail do Google ainda não verificado: `email_not_verified`.")
    @ApiResponse(responseCode = "423", description = "Login bloqueado por tentativas erradas: `blocked`.")
    @ApiResponse(
            responseCode = "503",
            description = "Login social desligado (sem `FIREBASE_PROJECT_ID`): `firebase_disabled`.")
    @PostMapping("/firebase")
    public MobileLoginResponse firebaseLogin(@Valid @RequestBody FirebaseLoginRequest request) {
        return response(firebase.execute(request.idToken()));
    }

    @Tag(name = "Usuário: conta")
    @Operation(
            summary = "Renovar a sessão do app",
            description = "Troca o refresh token por um par novo (rotação: o anterior deixa de valer).")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Sessão renovada.")
    @ApiResponse(
            responseCode = "401",
            description = "Refresh token ausente, inválido, expirado ou já usado: `invalid_refresh`.")
    @ApiResponse(responseCode = "423", description = "Login bloqueado por tentativas erradas: `blocked`.")
    @PostMapping("/mobile/refresh")
    public MobileLoginResponse mobileRefresh(@RequestBody(required = false) RefreshRequest body) {
        return response(renovar.renewUsuario(body == null ? null : body.refreshToken()));
    }

    /** Sessões não têm estado no servidor: o app descarta os tokens. Mantido para o contrato do cliente. */
    @Tag(name = "Usuário: conta")
    @Operation(
            summary = "Sair do app",
            description = "A sessão não tem estado no servidor: o app descarta os tokens. A rota existe para o "
                    + "contrato do cliente.")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Pronto.")
    @PostMapping("/mobile/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mobileLogout() {}

    private static MobileLoginResponse response(SessionIssuer.UsuarioSession session) {
        SessionIssuer.SessionTokens tokens = session.tokens();
        return new MobileLoginResponse(
                tokens.accessToken(), TOKEN_TYPE, tokens.expiresIn(), tokens.refreshToken(), session.user());
    }
}
