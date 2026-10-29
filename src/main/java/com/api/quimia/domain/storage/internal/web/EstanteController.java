package com.api.quimia.domain.storage.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.storage.internal.dto.GuardarProdutosRequest;
import com.api.quimia.domain.storage.internal.dto.GuardarProdutosResponse;
import com.api.quimia.domain.storage.internal.usecase.GuardarNaEstanteUseCase;
import com.api.quimia.domain.storage.internal.usecase.ListarEstanteUseCase;
import com.api.quimia.domain.storage.internal.usecase.RemoverDaEstanteUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Estante")
@RestController
@RequestMapping("/api/v1/estante/produtos")
public class EstanteController {
    private final GuardarNaEstanteUseCase guardar;
    private final ListarEstanteUseCase listar;
    private final RemoverDaEstanteUseCase remover;

    public EstanteController(
            GuardarNaEstanteUseCase guardar, ListarEstanteUseCase listar, RemoverDaEstanteUseCase remover) {
        this.guardar = guardar;
        this.listar = listar;
        this.remover = remover;
    }

    @Operation(
            summary = "Guardar produtos na estante",
            description = "Guarda de 1 a 50 produtos (\"Guardar todos\"). Responde 200 com o resultado de cada id: "
                    + "`adicionado`, `ja_na_estante` ou `nao_encontrado`.")
    @ApiResponse(responseCode = "200", description = "Resultado por produto.")
    @ApiResponse(responseCode = "404", description = "Usuário do token não existe mais: `usuario_nao_encontrado`.")
    @PostMapping
    public GuardarProdutosResponse guardar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody GuardarProdutosRequest request) {
        return new GuardarProdutosResponse(guardar.execute(principal.usuarioId(), request.produtoIds()));
    }

    @Operation(
            summary = "Listar a estante",
            description = "Produtos guardados pelo usuário, inclusive os que saíram do catálogo. "
                    + "`ordem=alfabetica` (padrão) ordena por nome; `recentes` e `antigos`, pela data em que o "
                    + "produto foi guardado.")
    @Parameter(name = "q", description = "Busca por nome, descrição ou marca (até 100 caracteres).", example = "cloro")
    @Parameter(name = "ordem", description = "alfabetica, recentes ou antigos.", example = "recentes")
    @Parameter(name = "page", description = "Página, a partir de 0.", example = "0")
    @Parameter(name = "size", description = "Itens por página, de 1 a 50.", example = "20")
    @ApiResponse(responseCode = "200", description = "Página de produtos.")
    @ApiResponse(
            responseCode = "400",
            description = "Paginação fora das regras (página negativa, tamanho fora de 1 a 50 ou página × tamanho "
                    + "acima do maior int): `paginacao_invalida`. Termo de busca acima de 100 caracteres: "
                    + "`termo_muito_longo`. Ordem fora da lista: `ordem_invalida`.")
    @GetMapping
    public Pagina<ProdutoView> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(name = "ordem", required = false) String ordem,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarEstanteUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.usuarioId(), termo, ordem, pagina, tamanho);
    }

    @Operation(
            summary = "Tirar produto da estante",
            description = "Tira o produto da estante, do cômodo e apaga as misturas com ele. Repetir não é erro.")
    @Parameter(name = "produtoId", description = "Id do produto.", example = "42")
    @ApiResponse(responseCode = "204", description = "Removido (ou já não estava).")
    @DeleteMapping("/{produtoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        remover.execute(principal.usuarioId(), produtoId);
    }
}
