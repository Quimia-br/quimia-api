package com.api.quimia.domain.safety.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.safety.dto.FdsDocumentoView;
import com.api.quimia.domain.safety.internal.usecase.ConsultarFdsDaEmpresaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** FDS de um produto da empresa no portal (titular empresa): o link do PDF para download. */
@Tag(name = "Empresa: produtos")
@RestController
public class FdsDaEmpresaController {
    private final ConsultarFdsDaEmpresaUseCase consultar;

    public FdsDaEmpresaController(ConsultarFdsDaEmpresaUseCase consultar) {
        this.consultar = consultar;
    }

    @Operation(
            summary = "Baixar a FDS do produto",
            description = "Link https do PDF da FDS ativa mais recente e o nome do arquivo. FDS gravada sem PDF "
                    + "do portal vem sem nome; `url` não https não é devolvida.")
    @Parameter(name = "produtoId", description = "Id do produto.", example = "42")
    @ApiResponse(responseCode = "200", description = "Dados do PDF.")
    @ApiResponse(
            responseCode = "404",
            description = "Produto sem vínculo com a empresa ou sem FDS ativa: `produto_nao_encontrado`, "
                    + "`fds_nao_encontrada`. Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @GetMapping("/api/v1/empresas/me/produtos/{produtoId}/fds")
    public FdsDocumentoView fds(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        return consultar.execute(principal.empresaId(), produtoId);
    }
}
