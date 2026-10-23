package com.api.quimia.domain.insights.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.insights.internal.dto.EstatisticasView;
import com.api.quimia.domain.insights.internal.dto.ResumoEmpresaView;
import com.api.quimia.domain.insights.internal.usecase.ConsultarEstatisticasUseCase;
import com.api.quimia.domain.insights.internal.usecase.ConsultarResumoUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/empresas/me")
public class InsightsController {
    private final ConsultarResumoUseCase resumo;
    private final ConsultarEstatisticasUseCase estatisticas;

    public InsightsController(ConsultarResumoUseCase resumo, ConsultarEstatisticasUseCase estatisticas) {
        this.resumo = resumo;
        this.estatisticas = estatisticas;
    }

    @GetMapping("/resumo")
    public ResumoEmpresaView resumo(@AuthenticationPrincipal AccountPrincipal principal) {
        return resumo.execute(principal.empresaId());
    }

    @GetMapping("/estatisticas")
    public EstatisticasView estatisticas(
            @AuthenticationPrincipal AccountPrincipal principal, @RequestParam(name = "q", required = false) String termo) {
        return estatisticas.execute(principal.empresaId(), termo);
    }
}
