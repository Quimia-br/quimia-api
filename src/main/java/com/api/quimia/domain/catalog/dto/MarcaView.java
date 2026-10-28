package com.api.quimia.domain.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Marca do produto.")
public record MarcaView(
        @Schema(description = "Id da marca.", example = "5")
        Integer id,
        @Schema(description = "Nome.", example = "Limpa Bem")
        String nome,
        @Schema(
                description = "URL https do logo.",
                example = "https://res.cloudinary.com/quimia/image/upload/marca.png")
        String logoUrl) {}
