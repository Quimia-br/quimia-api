package com.api.quimia.domain.insights.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.insights.internal.dto.EstatisticasView;
import com.api.quimia.domain.insights.internal.dto.ResumoEmpresaView;
import com.api.quimia.domain.insights.internal.usecase.ConsultarEstatisticasUseCase;
import com.api.quimia.domain.insights.internal.usecase.ConsultarResumoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Empresa: indicadores")
@RestController
@RequestMapping("/api/v1/empresas/me")
public class InsightsController {
    private final ConsultarResumoUseCase resumo;
    private final ConsultarEstatisticasUseCase estatisticas;

    public InsightsController(ConsultarResumoUseCase resumo, ConsultarEstatisticasUseCase estatisticas) {
        this.resumo = resumo;
        this.estatisticas = estatisticas;
    }

    @Operation(
            summary = "Resumo da Home do portal",
            description = "Produtos da empresa guardados em estantes, resumo das misturas com esses produtos (a "
                    + "taxa de sucesso ignora as não avaliadas) e o produto cadastrado mais recente. Só "
                    + "agregados.")
    @ApiResponse(responseCode = "200", description = "Resumo.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @GetMapping("/resumo")
    public ResumoEmpresaView resumo(@AuthenticationPrincipal AccountPrincipal principal) {
        return resumo.execute(principal.empresaId());
    }

    @Operation(
            summary = "Cards da tela Estatísticas",
            description = "Produtos em estantes e taxa de sucesso das misturas. `q` filtra os cards pelo título.")
    @Parameter(
            name = "q",
            description = "Filtro pelo título do card, sem acento nem caixa (até 100 caracteres).",
            example = "taxa")
    @ApiResponse(responseCode = "200", description = "Cards.")
    @ApiResponse(responseCode = "400", description = "Termo de busca acima de 100 caracteres: `termo_muito_longo`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @GetMapping("/estatisticas")
    public EstatisticasView estatisticas(
            @AuthenticationPrincipal AccountPrincipal principal, @RequestParam(name = "q", required = false) String termo) {
        return estatisticas.execute(principal.empresaId(), termo);
    }
}
