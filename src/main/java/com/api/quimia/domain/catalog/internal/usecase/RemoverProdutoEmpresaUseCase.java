package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.internal.config.CacheDoCatalogo;
import com.api.quimia.domain.catalog.internal.model.EmpresaProduto;
import com.api.quimia.domain.catalog.internal.persistence.EmpresaProdutoRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Deletar produto" no portal só desativa o vínculo da empresa: apagar o produto removeria, em
 * cascata, a estante e o histórico dos usuários. Idempotente para vínculo já desativado.
 */
@Service
public class RemoverProdutoEmpresaUseCase {
    private final EmpresaDoPortal portal;
    private final EmpresaProdutoRepository vinculos;

    public RemoverProdutoEmpresaUseCase(EmpresaDoPortal portal, EmpresaProdutoRepository vinculos) {
        this.portal = portal;
        this.vinculos = vinculos;
    }

    @CacheEvict(
            cacheNames = {
                CacheDoCatalogo.PRODUTO, CacheDoCatalogo.PRODUTO_POR_CODIGO, CacheDoCatalogo.BUSCA_DE_PRODUTOS
            },
            allEntries = true)
    @Transactional
    public void execute(Integer empresaId, Integer produtoId) {
        portal.exigirAtiva(empresaId);
        EmpresaProduto vinculo = vinculos.findByEmpresaIdAndProdutoId(empresaId, produtoId)
                .orElseThrow(CatalogException::produtoNaoEncontrado);
        vinculo.setAtivo(false);
    }
}
