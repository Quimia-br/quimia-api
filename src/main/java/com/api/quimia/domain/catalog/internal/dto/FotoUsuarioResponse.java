package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Foto de perfil gravada.")
public record FotoUsuarioResponse(
        @Schema(
                description = "URL https da foto nova, já salva no perfil.",
                example = "https://res.cloudinary.com/quimia/image/upload/quimia/perfil/foto.jpg")
        String fotoUrl) {}
