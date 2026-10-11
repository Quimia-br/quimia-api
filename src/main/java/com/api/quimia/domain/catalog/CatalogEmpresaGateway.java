package com.api.quimia.domain.catalog;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import java.util.List;
import java.util.Optional;

/** Contrato público dos produtos de uma empresa para outros módulos. Quem chama valida a empresa. */
public interface CatalogEmpresaGateway {
    /** Ids dos produtos com vínculo ativo da empresa (`ativo` nulo vale ativo); vazia se não há. */
    List<Integer> produtoIdsAtivos(Integer empresaId);

    /** Produto ativo com `empresa_produto.data_cadastro` mais recente; empate pelo maior id de produto. */
    Optional<ProdutoView> produtoMaisRecente(Integer empresaId);
}
