package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.TermoBusca;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Produtos cadastrados" do portal: só os produtos com vínculo ativo da própria empresa. */
@Service
public class ListarProdutosEmpresaUseCase {
    private final EmpresaDoPortal portal;
    private final ProdutoRepository produtos;

    public ListarProdutosEmpresaUseCase(EmpresaDoPortal portal, ProdutoRepository produtos) {
        this.portal = portal;
        this.produtos = produtos;
    }

    @Transactional(readOnly = true)
    public Pagina<ProdutoView> execute(
            Integer empresaId, String termo, String tipo, String codBarras, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > ListarProdutosUseCase.TAMANHO_MAXIMO) {
            throw new CatalogException("paginacao_invalida", 400);
        }
        if (termo != null && termo.length() > ListarProdutosUseCase.TERMO_MAXIMO) {
            throw new CatalogException("termo_muito_longo", 400);
        }
        portal.exigirAtiva(empresaId);
        return ProdutoMapper.pagina(produtos.buscarDaEmpresa(
                empresaId,
                TermoBusca.like(termo),
                tipo == null || tipo.isBlank() ? null : EmpresaDoPortal.tipoValido(tipo),
                CatalogException.codigoBarras(codBarras),
                PageRequest.of(pagina, tamanho, Sort.by("nome").and(Sort.by("id")))));
    }
}
