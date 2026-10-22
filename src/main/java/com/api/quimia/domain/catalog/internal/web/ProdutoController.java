package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.dto.BuscaCodigoBarrasRequest;
import com.api.quimia.domain.catalog.internal.usecase.ConsultarProdutoUseCase;
import com.api.quimia.domain.catalog.internal.usecase.ListarProdutosUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo compartilhado lido pelo app (titular usuário). */
@Tag(name = "Catálogo")
@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {
    private final ListarProdutosUseCase listar;
    private final ConsultarProdutoUseCase consultar;

    public ProdutoController(ListarProdutosUseCase listar, ConsultarProdutoUseCase consultar) {
        this.listar = listar;
        this.consultar = consultar;
    }

    @Operation(
            summary = "Listar produtos do catálogo",
            description = "Produtos visíveis no app (ao menos uma empresa com vínculo ativo), em ordem de nome. "
                    + "`q` procura em nome, descrição e marca, sem acento nem caixa. `codBarras` aceita UPC-A "
                    + "e EAN-13 com zero à esquerda como o mesmo código; espaços nas pontas são ignorados.")
    @Parameter(
            name = "q",
            description = "Busca por nome, descrição ou marca (até 100 caracteres).",
            example = "água sanitária")
    @Parameter(
            name = "tipo",
            description = "Tipo do produto: limpeza_geral, desinfetante, desincrustante, desengraxante, "
                    + "alvejante, aromatizante ou outro.",
            example = "alvejante")
    @Parameter(name = "codBarras", description = "Código de barras (UPC-A ou EAN-13).", example = "7891234567895")
    @Parameter(name = "page", description = "Página, a partir de 0.", example = "0")
    @Parameter(name = "size", description = "Itens por página, de 1 a 50.", example = "20")
    @ApiResponse(responseCode = "200", description = "Página de produtos.")
    @ApiResponse(
            responseCode = "400",
            description = "Paginação fora das regras (página negativa, tamanho fora de 1 a 50 ou página × tamanho "
                    + "acima do maior int): `paginacao_invalida`. Termo de busca acima de 100 caracteres: "
                    + "`termo_muito_longo`. Tipo fora da lista: `tipo_invalido`.")
    @GetMapping
    public Pagina<ProdutoView> listar(
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String codBarras,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarProdutosUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(termo, tipo, codBarras, pagina, tamanho);
    }

    @Operation(summary = "Detalhe do produto", description = "Produto do catálogo pelo id.")
    @Parameter(name = "id", description = "Id do produto.", example = "42")
    @ApiResponse(responseCode = "200", description = "Produto.")
    @ApiResponse(
            responseCode = "404",
            description = "Produto inexistente ou sem vínculo ativo: `produto_nao_encontrado`.")
    @GetMapping("/{id}")
    public ProdutoView detalhe(@PathVariable Integer id) {
        return consultar.porId(id);
    }

    @Operation(
            summary = "Buscar produto pelo código de barras lido",
            description = "Usado pelo leitor da câmera do app. Aceita UPC-A e EAN-13 com zero à esquerda como o "
                    + "mesmo código.")
    @ApiResponse(responseCode = "200", description = "Produto encontrado.")
    @ApiResponse(responseCode = "404", description = "Nenhum produto com esse código: `produto_nao_encontrado`.")
    @ApiResponse(
            responseCode = "422",
            description = "Código que não tem 8, 12, 13 ou 14 dígitos: `codigo_barras_invalido`.")
    @PostMapping("/busca-por-codigo-barras")
    public ProdutoView buscarPorCodigoBarras(@Valid @RequestBody BuscaCodigoBarrasRequest request) {
        return consultar.porCodigoBarras(request.codBarras());
    }
}
