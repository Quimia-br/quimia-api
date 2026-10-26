package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.dto.BuscaCodigoBarrasRequest;
import com.api.quimia.domain.catalog.internal.dto.BuscaPorImagemView;
import com.api.quimia.domain.catalog.internal.usecase.BuscarProdutosPorImagemUseCase;
import com.api.quimia.domain.catalog.internal.usecase.ConsultarProdutoUseCase;
import com.api.quimia.domain.catalog.internal.usecase.ListarProdutosUseCase;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Catálogo compartilhado lido pelo app (titular usuário). */
@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {
    private final ListarProdutosUseCase listar;
    private final ConsultarProdutoUseCase consultar;
    private final BuscarProdutosPorImagemUseCase buscarPorImagem;

    public ProdutoController(
            ListarProdutosUseCase listar,
            ConsultarProdutoUseCase consultar,
            BuscarProdutosPorImagemUseCase buscarPorImagem) {
        this.listar = listar;
        this.consultar = consultar;
        this.buscarPorImagem = buscarPorImagem;
    }

    @GetMapping
    public Pagina<ProdutoView> listar(
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String codBarras,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarProdutosUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(termo, tipo, codBarras, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public ProdutoView detalhe(@PathVariable Integer id) {
        return consultar.porId(id);
    }

    @PostMapping("/busca-por-codigo-barras")
    public ProdutoView buscarPorCodigoBarras(@Valid @RequestBody BuscaCodigoBarrasRequest request) {
        return consultar.porCodigoBarras(request.codBarras());
    }

    /** Escanear o rótulo: o app manda a foto (`imagem`) e recebe os produtos do catálogo que o agente reconheceu. */
    @PostMapping(path = "/busca-por-imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BuscaPorImagemView buscarPorImagem(
            @RequestParam(name = "imagem", required = false) MultipartFile imagem) {
        return buscarPorImagem.execute(imagem);
    }
}
