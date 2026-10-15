package com.api.quimia.domain.catalog.dto;

/** Produto do catálogo como exposto ao app e a outros módulos. `tipo` é o código do banco. */
public record ProdutoView(
        Integer id,
        String nome,
        String tipo,
        String descricao,
        String codBarras,
        String fotoUrl,
        MarcaView marca) {}
