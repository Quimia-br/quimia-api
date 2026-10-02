package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.internal.dto.MarcaResponse;
import com.api.quimia.domain.catalog.internal.dto.ProdutoResponse;
import com.api.quimia.domain.catalog.internal.model.Marca;
import com.api.quimia.domain.catalog.internal.model.Produto;

final class ProdutoViews {
    private ProdutoViews() {}

    static ProdutoResponse of(Produto produto) {
        Marca marca = produto.getMarca();
        return new ProdutoResponse(
                produto.getId(),
                produto.getNome(),
                produto.getTipo(),
                produto.getDescricao(),
                produto.getCodBarras(),
                produto.getFotoUrl(),
                new MarcaResponse(marca.getId(), marca.getNome(), marca.getLogoUrl()));
    }
}
