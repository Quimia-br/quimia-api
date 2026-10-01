package com.api.quimia.domain.account.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.account.internal.dto.UpdateEmpresaRequest;
import com.api.quimia.domain.account.internal.usecase.PerfilEmpresaUseCase;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/empresas/me")
public class EmpresaController {
    private final PerfilEmpresaUseCase perfil;

    public EmpresaController(PerfilEmpresaUseCase perfil) {
        this.perfil = perfil;
    }

    @GetMapping
    public EmpresaView me(@AuthenticationPrincipal AccountPrincipal principal) {
        return perfil.current(principal.empresaId());
    }

    @PatchMapping
    public EmpresaView update(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody UpdateEmpresaRequest request) {
        return perfil.update(principal.empresaId(), request);
    }
}
