package com.api.quimia.domain.catalog.internal.usecase;

import java.util.Map;
import org.springframework.data.domain.Sort;

/** `sort` e `direcao` da listagem do portal. Só campos da lista fechada; nulos por último nos dois sentidos. */
final class OrdenacaoDeProdutos {
    private static final Map<String, String> CAMPOS =
            Map.of("nome", "nome", "tipo", "tipo", "descricao", "descricao", "codBarras", "codBarras");

    private OrdenacaoDeProdutos() {}

    static Sort de(String sort, String direcao) {
        String campo = CAMPOS.get(sort == null ? "nome" : sort);
        if (campo == null) {
            throw new CatalogException("ordenacao_invalida", 400);
        }
        Sort.Direction sentido = switch (direcao == null ? "asc" : direcao) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new CatalogException("ordenacao_invalida", 400);
        };
        return Sort.by(new Sort.Order(sentido, campo).nullsLast(), Sort.Order.asc("id"));
    }
}
