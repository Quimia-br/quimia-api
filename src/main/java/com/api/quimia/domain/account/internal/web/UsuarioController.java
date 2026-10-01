package com.api.quimia.domain.account.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.account.dto.UserView;
import com.api.quimia.domain.account.internal.dto.LocalizacaoRequest;
import com.api.quimia.domain.account.internal.dto.LocalizacaoResponse;
import com.api.quimia.domain.account.internal.dto.UpdateProfileRequest;
import com.api.quimia.domain.account.internal.usecase.LocalizacaoUseCase;
import com.api.quimia.domain.account.internal.usecase.PerfilUseCase;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios/me")
public class UsuarioController {
    private final PerfilUseCase perfil;
    private final LocalizacaoUseCase localizacao;

    public UsuarioController(PerfilUseCase perfil, LocalizacaoUseCase localizacao) {
        this.perfil = perfil;
        this.localizacao = localizacao;
    }

    @GetMapping
    public UserView me(@AuthenticationPrincipal AccountPrincipal principal) {
        return perfil.current(principal.usuarioId());
    }

    @PatchMapping
    public UserView update(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return perfil.update(principal.usuarioId(), request);
    }

    @GetMapping("/localizacao")
    public LocalizacaoResponse localizacao(@AuthenticationPrincipal AccountPrincipal principal) {
        return localizacao.current(principal.usuarioId());
    }

    @PutMapping("/localizacao")
    public LocalizacaoResponse replaceLocalizacao(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody LocalizacaoRequest request) {
        return localizacao.replace(principal.usuarioId(), request);
    }
}
