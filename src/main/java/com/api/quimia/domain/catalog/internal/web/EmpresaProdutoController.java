package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.dto.AlteracaoProdutoForm;
import com.api.quimia.domain.catalog.internal.dto.CadastroProdutoForm;
import com.api.quimia.domain.catalog.internal.dto.CadastroProdutoResponse;
import com.api.quimia.domain.catalog.internal.usecase.AlterarProdutoEmpresaUseCase;
import com.api.quimia.domain.catalog.internal.usecase.CadastrarProdutoEmpresaUseCase;
import com.api.quimia.domain.catalog.internal.usecase.ListarProdutosEmpresaUseCase;
import com.api.quimia.domain.catalog.internal.usecase.ListarProdutosUseCase;
import com.api.quimia.domain.catalog.internal.usecase.RemoverProdutoEmpresaUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "Produtos cadastrados" do portal (titular empresa). Cadastro e alteração em multipart/form-data. */
@RestController
@RequestMapping("/api/v1/empresas/me/produtos")
public class EmpresaProdutoController {
    private final ListarProdutosEmpresaUseCase listar;
    private final CadastrarProdutoEmpresaUseCase cadastrar;
    private final AlterarProdutoEmpresaUseCase alterar;
    private final RemoverProdutoEmpresaUseCase remover;

    public EmpresaProdutoController(
            ListarProdutosEmpresaUseCase listar,
            CadastrarProdutoEmpresaUseCase cadastrar,
            AlterarProdutoEmpresaUseCase alterar,
            RemoverProdutoEmpresaUseCase remover) {
        this.listar = listar;
        this.cadastrar = cadastrar;
        this.alterar = alterar;
        this.remover = remover;
    }

    @GetMapping
    public Pagina<ProdutoView> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "q", required = false) String termo,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String codBarras,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarProdutosUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.empresaId(), termo, tipo, codBarras, pagina, tamanho);
    }

    /** `201` quando o produto foi criado; `200` quando a empresa foi vinculada ou reativada. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CadastroProdutoResponse> cadastrar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @ModelAttribute CadastroProdutoForm form) {
        CadastroProdutoResponse resposta = cadastrar.execute(principal.empresaId(), form);
        HttpStatus status = CadastrarProdutoEmpresaUseCase.CRIADO.equals(resposta.situacao())
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(resposta);
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProdutoView alterar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable Integer id,
            @Valid @ModelAttribute AlteracaoProdutoForm form) {
        return alterar.execute(principal.empresaId(), id, form);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer id) {
        remover.execute(principal.empresaId(), id);
    }
}
