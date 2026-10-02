package com.api.quimia.domain.catalog.internal.mapper;

import com.api.quimia.domain.catalog.dto.MarcaView;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.model.Marca;
import com.api.quimia.domain.catalog.internal.model.Produto;
import org.springframework.data.domain.Page;

public final class ProdutoMapper {
    private ProdutoMapper() {}

    public static ProdutoView view(Produto produto) {
        Marca marca = produto.getMarca();
        return new ProdutoView(
                produto.getId(),
                produto.getNome(),
                produto.getTipo(),
                produto.getDescricao(),
                produto.getCodBarras(),
                produto.getFotoUrl(),
                new MarcaView(marca.getId(), marca.getNome(), marca.getLogoUrl()));
    }

    public static Pagina<ProdutoView> pagina(Page<Produto> page) {
        return new Pagina<>(
                page.getContent().stream().map(ProdutoMapper::view).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
