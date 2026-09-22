package com.api.quimia.domain.account.internal.web;

import com.api.quimia.domain.account.dto.UserView;
import com.api.quimia.domain.account.internal.dto.UpdateProfileRequest;
import com.api.quimia.domain.account.internal.usecase.PerfilUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios/me")
public class UsuarioController {
    private final PerfilUseCase perfil;

    public UsuarioController(PerfilUseCase perfil) {
        this.perfil = perfil;
    }

    @GetMapping
    public UserView me(Authentication auth) {
        return perfil.current((UUID) auth.getPrincipal());
    }

    @PatchMapping
    public UserView update(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return perfil.update((UUID) auth.getPrincipal(), request);
    }
}
