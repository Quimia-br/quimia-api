package com.api.quimia.domain.storage.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.storage.internal.dto.ComodoRequest;
import com.api.quimia.domain.storage.internal.dto.ComodoResumo;
import com.api.quimia.domain.storage.internal.dto.ComodoView;
import com.api.quimia.domain.storage.internal.dto.IconeView;
import com.api.quimia.domain.storage.internal.dto.ProdutosNoComodoRequest;
import com.api.quimia.domain.storage.internal.model.IconeDeComodo;
import com.api.quimia.domain.storage.internal.usecase.AdicionarProdutosAoComodoUseCase;
import com.api.quimia.domain.storage.internal.usecase.ConsultarComodoDoProdutoUseCase;
import com.api.quimia.domain.storage.internal.usecase.ConsultarComodoUseCase;
import com.api.quimia.domain.storage.internal.usecase.CriarComodoUseCase;
import com.api.quimia.domain.storage.internal.usecase.ListarComodosUseCase;
import com.api.quimia.domain.storage.internal.usecase.RemoverProdutoDoComodoUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1")
public class ComodoController {
    private final CriarComodoUseCase criar;
    private final ListarComodosUseCase listar;
    private final ConsultarComodoUseCase consultar;
    private final AdicionarProdutosAoComodoUseCase adicionar;
    private final RemoverProdutoDoComodoUseCase remover;
    private final ConsultarComodoDoProdutoUseCase comodoDoProduto;

    public ComodoController(
            CriarComodoUseCase criar,
            ListarComodosUseCase listar,
            ConsultarComodoUseCase consultar,
            AdicionarProdutosAoComodoUseCase adicionar,
            RemoverProdutoDoComodoUseCase remover,
            ConsultarComodoDoProdutoUseCase comodoDoProduto) {
        this.criar = criar;
        this.listar = listar;
        this.consultar = consultar;
        this.adicionar = adicionar;
        this.remover = remover;
        this.comodoDoProduto = comodoDoProduto;
    }

    @PostMapping("/comodos")
    public ResponseEntity<ComodoView> criar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody ComodoRequest request) {
        ComodoView comodo = criar.execute(principal.usuarioId(), request);
        return ResponseEntity.created(URI.create("/api/v1/comodos/" + comodo.id())).body(comodo);
    }

    /** Catálogo de ícones aceitos em `POST /comodos`. */
    @GetMapping("/comodos/icones")
    public List<IconeView> icones() {
        return IconeDeComodo.todos().stream().map(IconeView::of).toList();
    }

    @GetMapping("/comodos")
    public Pagina<ComodoResumo> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarComodosUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.usuarioId(), termo, pagina, tamanho);
    }

    @GetMapping("/comodos/{id}")
    public ComodoView consultar(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer id) {
        return consultar.execute(principal.usuarioId(), id);
    }

    @PostMapping("/comodos/{id}/produtos")
    public ComodoView adicionar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable Integer id,
            @Valid @RequestBody ProdutosNoComodoRequest request) {
        return adicionar.execute(principal.usuarioId(), id, request);
    }

    @DeleteMapping("/comodos/{id}/produtos/{produtoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer id, @PathVariable Integer produtoId) {
        remover.execute(principal.usuarioId(), id, produtoId);
    }

    @GetMapping("/estante/produtos/{produtoId}/comodo")
    public ComodoResumo doProduto(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        return comodoDoProduto.execute(principal.usuarioId(), produtoId);
    }
}
