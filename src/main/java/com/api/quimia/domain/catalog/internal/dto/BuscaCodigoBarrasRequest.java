package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Código lido pela câmera.")
public record BuscaCodigoBarrasRequest(
        @Schema(description = "Código com 8, 12, 13 ou 14 dígitos.", example = "7891234567895")
        @NotBlank @Size(max = 64) String codBarras) {}
