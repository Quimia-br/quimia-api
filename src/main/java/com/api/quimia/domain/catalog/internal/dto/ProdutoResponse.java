package com.api.quimia.domain.catalog.internal.dto;

public record ProdutoResponse(
        Integer id,
        String nome,
        String tipo,
        String descricao,
        String codBarras,
        String fotoUrl,
        MarcaResponse marca) {}
