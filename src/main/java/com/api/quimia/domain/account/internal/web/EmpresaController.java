package com.api.quimia.domain.account.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.account.internal.dto.UpdateEmpresaRequest;
import com.api.quimia.domain.account.internal.usecase.PerfilEmpresaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Empresa: conta")
@RestController
@RequestMapping("/api/v1/empresas/me")
public class EmpresaController {
    private final PerfilEmpresaUseCase perfil;

    public EmpresaController(PerfilEmpresaUseCase perfil) {
        this.perfil = perfil;
    }

    @Operation(summary = "Perfil da empresa", description = "Dados da empresa do token.")
    @ApiResponse(responseCode = "200", description = "Perfil.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `not_found`.")
    @GetMapping
    public EmpresaView me(@AuthenticationPrincipal AccountPrincipal principal) {
        return perfil.current(principal.empresaId());
    }

    @Operation(
            summary = "Alterar o perfil da empresa",
            description = "Altera só os campos enviados (nome, e-mail, URL do logo). Para enviar o arquivo do "
                    + "logo use `POST /empresas/me/foto`.")
    @ApiResponse(responseCode = "200", description = "Perfil alterado.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `not_found`.")
    @ApiResponse(responseCode = "409", description = "E-mail já usado por outra empresa: `email_in_use`.")
    @PatchMapping
    public EmpresaView update(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody UpdateEmpresaRequest request) {
        return perfil.update(principal.empresaId(), request);
    }
}
