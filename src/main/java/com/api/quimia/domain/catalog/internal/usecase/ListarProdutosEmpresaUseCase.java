package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.CodigoBarras;
import com.api.quimia.domain.catalog.internal.model.TermoBusca;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import java.util.List;
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
            Integer empresaId,
            String termo,
            List<String> fields,
            String tipo,
            String codBarras,
            String sort,
            String direcao,
            int pagina,
            int tamanho) {
        Sort ordenacao = OrdenacaoDeProdutos.de(sort, direcao);
        if (pagina < 0 || tamanho < 1 || tamanho > ListarProdutosUseCase.TAMANHO_MAXIMO
                || ListarProdutosUseCase.offsetExcedeLimite(pagina, tamanho)) {
            throw new CatalogException("paginacao_invalida", 400);
        }
        if (termo != null && termo.length() > ListarProdutosUseCase.TERMO_MAXIMO) {
            throw new CatalogException("termo_muito_longo", 400);
        }
        CamposDaBusca campos = CamposDaBusca.de(fields);
        portal.exigirAtiva(empresaId);
        String codigo = CatalogException.codigoBarras(codBarras);
        return ProdutoMapper.pagina(produtos.buscarDaEmpresa(
                empresaId,
                TermoBusca.like(termo),
                campos.nome(),
                campos.descricaoEMarca(),
                campos.tipo(),
                CamposDaBusca.tiposDoTermo(termo),
                campos.codBarras(),
                tipo == null || tipo.isBlank() ? null : EmpresaDoPortal.tipoValido(tipo),
                codigo == null,
                CodigoBarras.paraFiltro(codigo),
                PageRequest.of(pagina, tamanho, ordenacao)));
    }
}
