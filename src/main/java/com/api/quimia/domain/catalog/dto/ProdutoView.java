package com.api.quimia.domain.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Produto do catálogo como exposto ao app e a outros módulos. `tipo` é o código do banco. */
@Schema(description = "Produto do catálogo.")
public record ProdutoView(
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
        @Schema(description = "Marca (no portal, a própria empresa).")
        MarcaView marca) {}
