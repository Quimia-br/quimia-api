package com.api.quimia.domain.storage.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.storage.internal.dto.GuardarProdutosRequest;
import com.api.quimia.domain.storage.internal.dto.GuardarProdutosResponse;
import com.api.quimia.domain.storage.internal.usecase.GuardarNaEstanteUseCase;
import com.api.quimia.domain.storage.internal.usecase.ListarEstanteUseCase;
import com.api.quimia.domain.storage.internal.usecase.RemoverDaEstanteUseCase;
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

    @PostMapping
    public GuardarProdutosResponse guardar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody GuardarProdutosRequest request) {
        return new GuardarProdutosResponse(guardar.execute(principal.usuarioId(), request.produtoIds()));
    }

    @GetMapping
    public Pagina<ProdutoView> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarEstanteUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.usuarioId(), termo, pagina, tamanho);
    }

    @DeleteMapping("/{produtoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        remover.execute(principal.usuarioId(), produtoId);
    }
}
