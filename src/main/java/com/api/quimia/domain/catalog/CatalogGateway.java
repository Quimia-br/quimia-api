package com.api.quimia.domain.catalog;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import java.util.Collection;
import java.util.List;

/** Contrato público do catálogo para outros módulos. */
public interface CatalogGateway {
    /** Produtos visíveis no app entre os ids informados; inexistentes ou ocultos ficam de fora. */
    List<ProdutoView> visiveis(Collection<Integer> ids);

    /**
     * Produtos dos ids informados, mesmo os que ficaram ocultos depois (ex.: já guardados na
     * estante), com busca por termo, ordenados por nome. O chamador valida a paginação.
     */
    Pagina<ProdutoView> listarPorIds(Collection<Integer> ids, String termo, int pagina, int tamanho);
}
