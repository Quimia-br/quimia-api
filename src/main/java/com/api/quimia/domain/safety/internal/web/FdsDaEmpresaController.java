package com.api.quimia.domain.safety.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.safety.dto.FdsDocumentoView;
import com.api.quimia.domain.safety.internal.usecase.ConsultarFdsDaEmpresaUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** FDS de um produto da empresa no portal (titular empresa): o link do PDF para download. */
@RestController
public class FdsDaEmpresaController {
    private final ConsultarFdsDaEmpresaUseCase consultar;

    public FdsDaEmpresaController(ConsultarFdsDaEmpresaUseCase consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/api/v1/empresas/me/produtos/{produtoId}/fds")
    public FdsDocumentoView fds(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        return consultar.execute(principal.empresaId(), produtoId);
    }
}
