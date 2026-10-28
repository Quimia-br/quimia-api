package com.api.quimia.domain.storage.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.storage.internal.dto.ComodoRequest;
import com.api.quimia.domain.storage.internal.dto.ComodoResumo;
import com.api.quimia.domain.storage.internal.dto.ComodoView;
import com.api.quimia.domain.storage.internal.dto.ProdutosNoComodoRequest;
import com.api.quimia.domain.storage.internal.usecase.AdicionarProdutosAoComodoUseCase;
import com.api.quimia.domain.storage.internal.usecase.ConsultarComodoDoProdutoUseCase;
import com.api.quimia.domain.storage.internal.usecase.ConsultarComodoUseCase;
import com.api.quimia.domain.storage.internal.usecase.CriarComodoUseCase;
import com.api.quimia.domain.storage.internal.usecase.ListarComodosUseCase;
import com.api.quimia.domain.storage.internal.usecase.RemoverProdutoDoComodoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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

@Tag(name = "Cômodos")
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

    @Operation(
            summary = "Criar cômodo",
            description = "Cria o cômodo com 2 a 20 produtos da estante e devolve a avaliação de risco de todos "
                    + "os pares. O nome é único por usuário (sem acento nem caixa). Um produto fica em no "
                    + "máximo um cômodo.")
    @ApiResponse(
            responseCode = "201",
            description = "Cômodo criado.",
            headers = @Header(name = "Location", description = "URL do cômodo criado (`/api/v1/comodos/{id}`)."))
    @ApiResponse(
            responseCode = "409",
            description = "Nome repetido ou produto já em outro cômodo: `comodo_ja_existe`, "
                    + "`produto_em_outro_comodo`.")
    @ApiResponse(
            responseCode = "422",
            description = "Menos de 2 ou mais de 20 produtos, ou produto fora da estante: "
                    + "`comodo_produtos_insuficientes`, `comodo_produtos_demais`, `produto_fora_da_estante`.")
    @PostMapping("/comodos")
    public ResponseEntity<ComodoView> criar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody ComodoRequest request) {
        ComodoView comodo = criar.execute(principal.usuarioId(), request);
        return ResponseEntity.created(URI.create("/api/v1/comodos/" + comodo.id())).body(comodo);
    }

    @Operation(
            summary = "Listar cômodos",
            description = "Cômodos do usuário por nome (sem acento), com o total de produtos e o status de risco "
                    + "de cada um.")
    @Parameter(name = "q", description = "Busca pelo nome do cômodo (até 100 caracteres).", example = "banheiro")
    @Parameter(name = "page", description = "Página, a partir de 0.", example = "0")
    @Parameter(name = "size", description = "Itens por página, de 1 a 50.", example = "20")
    @ApiResponse(responseCode = "200", description = "Página de cômodos.")
    @ApiResponse(
            responseCode = "400",
            description = "Paginação fora das regras (página negativa, tamanho fora de 1 a 50 ou página × tamanho "
                    + "acima do maior int): `paginacao_invalida`. Termo de busca acima de 100 caracteres: "
                    + "`termo_muito_longo`.")
    @GetMapping("/comodos")
    public Pagina<ComodoResumo> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarComodosUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.usuarioId(), termo, pagina, tamanho);
    }

    @Operation(
            summary = "Detalhe do cômodo",
            description = "Produtos do cômodo (por nome) e a avaliação de risco recalculada na hora.")
    @Parameter(name = "id", description = "Id do cômodo.", example = "3")
    @ApiResponse(responseCode = "200", description = "Cômodo.")
    @ApiResponse(responseCode = "404", description = "Cômodo inexistente ou de outro usuário: `comodo_nao_encontrado`.")
    @GetMapping("/comodos/{id}")
    public ComodoView consultar(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer id) {
        return consultar.execute(principal.usuarioId(), id);
    }

    @Operation(
            summary = "Adicionar produtos ao cômodo",
            description = "Liga produtos da estante ao cômodo (o que já estava nele não é erro). O total não "
                    + "passa de 20.")
    @Parameter(name = "id", description = "Id do cômodo.", example = "3")
    @ApiResponse(responseCode = "200", description = "Cômodo recalculado.")
    @ApiResponse(responseCode = "404", description = "Cômodo inexistente ou de outro usuário: `comodo_nao_encontrado`.")
    @ApiResponse(responseCode = "409", description = "Produto já em outro cômodo: `produto_em_outro_comodo`.")
    @ApiResponse(
            responseCode = "422",
            description = "Mais de 20 produtos ou produto fora da estante: `comodo_produtos_demais`, "
                    + "`produto_fora_da_estante`.")
    @PostMapping("/comodos/{id}/produtos")
    public ComodoView adicionar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable Integer id,
            @Valid @RequestBody ProdutosNoComodoRequest request) {
        return adicionar.execute(principal.usuarioId(), id, request);
    }

    @Operation(
            summary = "Tirar produto do cômodo",
            description = "O produto continua na estante. Repetir não é erro; o cômodo pode ficar com menos de 2 "
                    + "produtos.")
    @Parameter(name = "id", description = "Id do cômodo.", example = "3")
    @Parameter(name = "produtoId", description = "Id do produto.", example = "42")
    @ApiResponse(responseCode = "204", description = "Removido do cômodo.")
    @ApiResponse(responseCode = "404", description = "Cômodo inexistente ou de outro usuário: `comodo_nao_encontrado`.")
    @DeleteMapping("/comodos/{id}/produtos/{produtoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer id, @PathVariable Integer produtoId) {
        remover.execute(principal.usuarioId(), id, produtoId);
    }

    @Operation(summary = "Cômodo de um produto da estante", description = "Resumo do cômodo em que o produto está.")
    @Parameter(name = "produtoId", description = "Id do produto da estante.", example = "42")
    @ApiResponse(responseCode = "200", description = "Cômodo do produto.")
    @ApiResponse(
            responseCode = "404",
            description = "Produto fora da estante ou sem cômodo: `produto_nao_esta_na_estante`, "
                    + "`produto_sem_comodo`.")
    @GetMapping("/estante/produtos/{produtoId}/comodo")
    public ComodoResumo doProduto(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer produtoId) {
        return comodoDoProduto.execute(principal.usuarioId(), produtoId);
    }
}
