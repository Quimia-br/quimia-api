package com.api.quimia.domain.recommendation.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Par a misturar.")
public record MisturaRequest(
        @Schema(description = "Id de um produto da estante.", example = "42")
        @NotNull Integer produtoIdA,
        @Schema(description = "Id de outro produto da estante.", example = "57")
        @NotNull Integer produtoIdB) {}
