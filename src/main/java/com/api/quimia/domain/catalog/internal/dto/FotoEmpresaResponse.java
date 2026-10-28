package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Logo gravado.")
public record FotoEmpresaResponse(
        @Schema(
                description = "URL https do logo novo.",
                example = "https://res.cloudinary.com/quimia/image/upload/logo.png")
        String fotoUrl) {}
