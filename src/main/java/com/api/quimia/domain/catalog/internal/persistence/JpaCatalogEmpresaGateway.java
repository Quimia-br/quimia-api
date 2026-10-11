package com.api.quimia.domain.catalog.internal.persistence;

import com.api.quimia.domain.catalog.CatalogEmpresaGateway;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaCatalogEmpresaGateway implements CatalogEmpresaGateway {
    private final EmpresaProdutoRepository vinculos;
    private final ProdutoRepository produtos;

    public JpaCatalogEmpresaGateway(EmpresaProdutoRepository vinculos, ProdutoRepository produtos) {
        this.vinculos = vinculos;
        this.produtos = produtos;
    }

    @Override
    public List<Integer> produtoIdsAtivos(Integer empresaId) {
        return vinculos.produtoIdsAtivos(empresaId);
    }

    @Override
    public Optional<ProdutoView> produtoMaisRecente(Integer empresaId) {
        return produtos.maisRecentesDaEmpresa(empresaId, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(ProdutoMapper::view);
    }
}
