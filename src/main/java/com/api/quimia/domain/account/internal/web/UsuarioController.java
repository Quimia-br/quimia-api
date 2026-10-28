package com.api.quimia.domain.account.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.account.dto.UserView;
import com.api.quimia.domain.account.internal.dto.LocalizacaoRequest;
import com.api.quimia.domain.account.internal.dto.LocalizacaoResponse;
import com.api.quimia.domain.account.internal.dto.UpdateProfileRequest;
import com.api.quimia.domain.account.internal.usecase.LocalizacaoUseCase;
import com.api.quimia.domain.account.internal.usecase.PerfilUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Usuário: conta")
@RestController
@RequestMapping("/api/v1/usuarios/me")
public class UsuarioController {
    private final PerfilUseCase perfil;
    private final LocalizacaoUseCase localizacao;

    public UsuarioController(PerfilUseCase perfil, LocalizacaoUseCase localizacao) {
        this.perfil = perfil;
        this.localizacao = localizacao;
    }

    @Operation(summary = "Perfil do usuário", description = "Dados do usuário do token.")
    @ApiResponse(responseCode = "200", description = "Perfil.")
    @ApiResponse(responseCode = "404", description = "Usuário do token não existe mais: `not_found`.")
    @GetMapping
    public UserView me(@AuthenticationPrincipal AccountPrincipal principal) {
        return perfil.current(principal.usuarioId());
    }

    @Operation(
            summary = "Alterar o perfil",
            description = "Altera só os campos enviados (nome, data de nascimento, foto).")
    @ApiResponse(responseCode = "200", description = "Perfil alterado.")
    @ApiResponse(responseCode = "404", description = "Usuário do token não existe mais: `not_found`.")
    @PatchMapping
    public UserView update(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return perfil.update(principal.usuarioId(), request);
    }

    @Operation(
            summary = "Endereço do usuário",
            description = "Endereço cadastrado (um por usuário). Usado em Pontos com "
                    + "`usarLocalizacaoCadastrada=true`.")
    @ApiResponse(responseCode = "200", description = "Endereço.")
    @ApiResponse(responseCode = "404", description = "Usuário sem endereço cadastrado: `not_found`.")
    @GetMapping("/localizacao")
    public LocalizacaoResponse localizacao(@AuthenticationPrincipal AccountPrincipal principal) {
        return localizacao.current(principal.usuarioId());
    }

    @Operation(summary = "Gravar o endereço do usuário", description = "Cria ou substitui o endereço inteiro.")
    @ApiResponse(responseCode = "200", description = "Endereço gravado.")
    @ApiResponse(responseCode = "404", description = "Usuário do token não existe mais: `not_found`.")
    @ApiResponse(responseCode = "422", description = "CEP que não tem 8 dígitos: `invalid_cep`.")
    @PutMapping("/localizacao")
    public LocalizacaoResponse replaceLocalizacao(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody LocalizacaoRequest request) {
        return localizacao.replace(principal.usuarioId(), request);
    }
}
