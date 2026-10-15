package com.api.quimia.domain.storage.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.safety.dto.ResumoSimplificadoView;
import com.api.quimia.domain.storage.internal.dto.SubstanciasDaEstanteResponse;
import com.api.quimia.domain.storage.internal.usecase.ConsultarResumoDaEstanteUseCase;
import com.api.quimia.domain.storage.internal.usecase.ListarSubstanciasDaEstanteUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Segurança química a partir da estante do usuário. Tudo é leitura. */
@RestController
@RequestMapping("/api/v1/estante")
public class EstanteSegurancaController {
    private final ConsultarResumoDaEstanteUseCase resumo;
    private final ListarSubstanciasDaEstanteUseCase substancias;

    public EstanteSegurancaController(
            ConsultarResumoDaEstanteUseCase resumo, ListarSubstanciasDaEstanteUseCase substancias) {
        this.resumo = resumo;
        this.substancias = substancias;
    }

    @GetMapping("/produtos/{produtoId}/resumo-simplificado")
    public ResumoSimplificadoView resumo(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        return resumo.execute(principal.usuarioId(), produtoId);
    }

    @GetMapping("/substancias")
    public SubstanciasDaEstanteResponse substancias(@AuthenticationPrincipal AccountPrincipal principal) {
        return new SubstanciasDaEstanteResponse(substancias.execute(principal.usuarioId()));
    }
}
