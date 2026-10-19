package com.api.quimia.domain.storage.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.safety.dto.PrimeirosSocorrosView;
import com.api.quimia.domain.safety.dto.ResumoSimplificadoView;
import com.api.quimia.domain.storage.internal.dto.SubstanciasDaEstanteResponse;
import com.api.quimia.domain.storage.internal.usecase.ConsultarPrimeirosSocorrosDaEstanteUseCase;
import com.api.quimia.domain.storage.internal.usecase.ConsultarResumoDaEstanteUseCase;
import com.api.quimia.domain.storage.internal.usecase.ListarSubstanciasDaEstanteUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Segurança química a partir da estante do usuário. Tudo é leitura. */
@RestController
@RequestMapping("/api/v1/estante")
public class EstanteSegurancaController {
    private final ConsultarResumoDaEstanteUseCase resumo;
    private final ConsultarPrimeirosSocorrosDaEstanteUseCase primeirosSocorros;
    private final ListarSubstanciasDaEstanteUseCase substancias;

    public EstanteSegurancaController(
            ConsultarResumoDaEstanteUseCase resumo,
            ConsultarPrimeirosSocorrosDaEstanteUseCase primeirosSocorros,
            ListarSubstanciasDaEstanteUseCase substancias) {
        this.resumo = resumo;
        this.primeirosSocorros = primeirosSocorros;
        this.substancias = substancias;
    }

    @GetMapping("/produtos/{produtoId}/resumo-simplificado")
    public ResumoSimplificadoView resumo(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        return resumo.execute(principal.usuarioId(), produtoId);
    }

    /** "Como se proteger?": primeiros socorros da FDS do produto. */
    @GetMapping("/produtos/{produtoId}/primeiros-socorros")
    public PrimeirosSocorrosView primeirosSocorros(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        return primeirosSocorros.execute(principal.usuarioId(), produtoId);
    }

    @GetMapping("/substancias")
    public SubstanciasDaEstanteResponse substancias(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(name = "ordem", required = false) String ordem) {
        return new SubstanciasDaEstanteResponse(substancias.execute(principal.usuarioId(), termo, ordem));
    }
}
