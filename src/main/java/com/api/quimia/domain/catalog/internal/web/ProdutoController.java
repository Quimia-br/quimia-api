package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.catalog.internal.dto.BuscaCodigoBarrasRequest;
import com.api.quimia.domain.catalog.internal.dto.PaginaResponse;
import com.api.quimia.domain.catalog.internal.dto.ProdutoResponse;
import com.api.quimia.domain.catalog.internal.usecase.ConsultarProdutoUseCase;
import com.api.quimia.domain.catalog.internal.usecase.ListarProdutosUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo compartilhado lido pelo app (titular usuário). */
@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {
    private final ListarProdutosUseCase listar;
    private final ConsultarProdutoUseCase consultar;

    public ProdutoController(ListarProdutosUseCase listar, ConsultarProdutoUseCase consultar) {
        this.listar = listar;
        this.consultar = consultar;
    }

    @GetMapping
    public PaginaResponse<ProdutoResponse> listar(
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String codBarras,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarProdutosUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(termo, tipo, codBarras, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public ProdutoResponse detalhe(@PathVariable Integer id) {
        return consultar.porId(id);
    }

    @PostMapping("/busca-por-codigo-barras")
    public ProdutoResponse buscarPorCodigoBarras(@Valid @RequestBody BuscaCodigoBarrasRequest request) {
        return consultar.porCodigoBarras(request.codBarras());
    }
}
