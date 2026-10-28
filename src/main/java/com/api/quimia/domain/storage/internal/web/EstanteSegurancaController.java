package com.api.quimia.domain.storage.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.safety.dto.PrimeirosSocorrosView;
import com.api.quimia.domain.safety.dto.ResumoSimplificadoView;
import com.api.quimia.domain.storage.internal.dto.SubstanciasDaEstanteResponse;
import com.api.quimia.domain.storage.internal.usecase.ConsultarPrimeirosSocorrosDaEstanteUseCase;
import com.api.quimia.domain.storage.internal.usecase.ConsultarResumoDaEstanteUseCase;
import com.api.quimia.domain.storage.internal.usecase.ListarSubstanciasDaEstanteUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Segurança química a partir da estante do usuário. Tudo é leitura. */
@Tag(name = "Segurança química")
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

    @Operation(
            summary = "Resumo simplificado do rótulo",
            description = "Texto de `fds.resumo_simplificado` da FDS ativa mais recente. Campos nulos sem FDS ou "
                    + "sem texto.")
    @Parameter(name = "produtoId", description = "Id do produto da estante.", example = "42")
    @ApiResponse(responseCode = "200", description = "Resumo.")
    @ApiResponse(
            responseCode = "404",
            description = "Produto fora da estante do usuário: `produto_nao_esta_na_estante`.")
    @GetMapping("/produtos/{produtoId}/resumo-simplificado")
    public ResumoSimplificadoView resumo(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        return resumo.execute(principal.usuarioId(), produtoId);
    }

    /** "Como se proteger?": primeiros socorros da FDS do produto. */
    @Operation(
            summary = "Como se proteger (primeiros socorros)",
            description = "Seção 4 da FDS ativa mais recente: um registro por rota de exposição (inalacao, pele, "
                    + "olhos, ingestao, nessa ordem), orientações gerais e notas ao médico. "
                    + "`encontrada=false` (listas vazias) quando não há FDS, a FDS não tem a seção ou o "
                    + "conteúdo está fora do contrato.")
    @Parameter(name = "produtoId", description = "Id do produto da estante.", example = "42")
    @ApiResponse(responseCode = "200", description = "Primeiros socorros.")
    @ApiResponse(
            responseCode = "404",
            description = "Produto fora da estante do usuário: `produto_nao_esta_na_estante`.")
    @GetMapping("/produtos/{produtoId}/primeiros-socorros")
    public PrimeirosSocorrosView primeirosSocorros(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        return primeirosSocorros.execute(principal.usuarioId(), produtoId);
    }

    @Operation(
            summary = "Substâncias da estante",
            description = "Substâncias presentes nos produtos da estante, cada uma com a descrição e os produtos "
                    + "em que aparece.")
    @Parameter(name = "q", description = "Busca pelo nome da substância (até 100 caracteres).", example = "hipoclorito")
    @Parameter(name = "ordem", description = "alfabetica.", example = "alfabetica")
    @ApiResponse(responseCode = "200", description = "Substâncias.")
    @ApiResponse(
            responseCode = "400",
            description = "Termo de busca acima de 100 caracteres: `termo_muito_longo`. Ordem fora da lista: "
                    + "`ordem_invalida`.")
    @GetMapping("/substancias")
    public SubstanciasDaEstanteResponse substancias(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(name = "ordem", required = false) String ordem) {
        return new SubstanciasDaEstanteResponse(substancias.execute(principal.usuarioId(), termo, ordem));
    }
}
