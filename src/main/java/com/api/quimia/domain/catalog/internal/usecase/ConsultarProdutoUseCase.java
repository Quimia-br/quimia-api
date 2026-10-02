package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.Produto;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarProdutoUseCase {
    private static final int UPC_A = 12;
    private static final int EAN_13 = 13;

    private final ProdutoRepository produtos;

    public ConsultarProdutoUseCase(ProdutoRepository produtos) {
        this.produtos = produtos;
    }

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
    @Transactional(readOnly = true)
    public ProdutoView porCodigoBarras(String codBarras) {
        String codigo = CatalogException.codigoBarras(codBarras);
        List<Produto> encontrados = produtos.findVisiveisPorCodigos(variacoes(codigo));
        return encontrados.stream()
                .filter(produto -> codigo.equals(produto.getCodBarras()))
                .findFirst()
                .or(() -> encontrados.stream().findFirst())
                .map(ProdutoMapper::view)
                .orElseThrow(CatalogException::produtoNaoEncontrado);
    }

    private static Set<String> variacoes(String codigo) {
        Set<String> codigos = new LinkedHashSet<>();
        codigos.add(codigo);
        if (codigo.length() == UPC_A) {
            codigos.add("0" + codigo);
        }
        if (codigo.length() == EAN_13 && codigo.startsWith("0")) {
            codigos.add(codigo.substring(1));
        }
        return codigos;
    }
}
