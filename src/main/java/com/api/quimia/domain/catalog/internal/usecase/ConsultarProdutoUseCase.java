package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.config.CacheDoCatalogo;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.CodigoBarras;
import com.api.quimia.domain.catalog.internal.model.Produto;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarProdutoUseCase {
    private final ProdutoRepository produtos;

    public ConsultarProdutoUseCase(ProdutoRepository produtos) {
        this.produtos = produtos;
    }

    @Cacheable(CacheDoCatalogo.PRODUTO)
    @Transactional(readOnly = true)
    public ProdutoView porId(Integer id) {
        return produtos.findVisivelById(id)
                .map(ProdutoMapper::view)
                .orElseThrow(CatalogException::produtoNaoEncontrado);
    }

    /**
     * Escanear rótulo. Leitores devolvem o mesmo produto como UPC-A (12 dígitos) ou EAN-13 com
     * `0` inicial, então a busca tenta as duas formas e prefere o código exato.
     */
    @Cacheable(cacheNames = CacheDoCatalogo.PRODUTO_POR_CODIGO, condition = "#codBarras != null")
    @Transactional(readOnly = true)
    public ProdutoView porCodigoBarras(String codBarras) {
        String codigo = CatalogException.codigoBarras(codBarras);
        List<Produto> encontrados = produtos.findVisiveisPorCodigos(CodigoBarras.equivalentes(codigo));
        return encontrados.stream()
                .filter(produto -> codigo.equals(produto.getCodBarras()))
                .findFirst()
                .or(() -> encontrados.stream().findFirst())
                .map(ProdutoMapper::view)
                .orElseThrow(CatalogException::produtoNaoEncontrado);
    }
}
