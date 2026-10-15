package com.api.quimia.domain.catalog.internal.persistence;

import com.api.quimia.domain.catalog.CatalogGateway;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.TermoBusca;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaCatalogGateway implements CatalogGateway {
    private final ProdutoRepository produtos;

    public JpaCatalogGateway(ProdutoRepository produtos) {
        this.produtos = produtos;
    }

    @Override
    public List<ProdutoView> visiveis(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return produtos.findVisiveisPorIds(ids).stream().map(ProdutoMapper::view).toList();
    }

    @Override
    public Pagina<ProdutoView> listarPorIds(Collection<Integer> ids, String termo, int pagina, int tamanho) {
        if (ids.isEmpty()) {
            return Pagina.vazia(pagina, tamanho);
        }
        return ProdutoMapper.pagina(produtos.buscarPorIds(
                ids, TermoBusca.like(termo), PageRequest.of(pagina, tamanho, Sort.by("nome").and(Sort.by("id")))));
    }
}
