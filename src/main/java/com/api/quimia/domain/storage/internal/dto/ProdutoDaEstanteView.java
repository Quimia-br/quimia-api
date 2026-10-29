package com.api.quimia.domain.storage.internal.dto;

import com.api.quimia.domain.catalog.dto.MarcaView;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import io.swagger.v3.oas.annotations.media.Schema;

/** Produto do catálogo como aparece na estante do usuário: os campos de {@link ProdutoView} e o favorito. */
@Schema(description = "Produto guardado na estante.")
public record ProdutoDaEstanteView(
        @Schema(description = "Id do produto.", example = "42")
        Integer id,
        @Schema(description = "Nome.", example = "Água Sanitária Limpa Bem")
        String nome,
        @Schema(
                description = "Tipo: limpeza_geral, desinfetante, desincrustante, desengraxante, alvejante, "
                        + "aromatizante ou outro.",
                example = "alvejante")
        String tipo,
        @Schema(description = "Descrição.", example = "Alvejante com cloro ativo 2,5%.")
        String descricao,
        @Schema(description = "Código de barras (UPC-A ou EAN-13).", example = "7891234567895")
        String codBarras,
        @Schema(
                description = "URL https da foto.",
                example = "https://res.cloudinary.com/quimia/image/upload/produto.jpg")
        String fotoUrl,
        @Schema(description = "Marca.")
        MarcaView marca,
        @Schema(description = "Marcado como favorito pelo usuário.", example = "true")
        boolean favorito) {
    public static ProdutoDaEstanteView of(ProdutoView produto, boolean favorito) {
        return new ProdutoDaEstanteView(
                produto.id(),
                produto.nome(),
                produto.tipo(),
                produto.descricao(),
                produto.codBarras(),
                produto.fotoUrl(),
                produto.marca(),
                favorito);
    }
}
