package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Par de produtos incompatível.")
public record ParIncompativel(
        @Schema(description = "Menor id do par.", example = "42")
        Integer produtoIdA,
        @Schema(description = "Maior id do par.", example = "57")
        Integer produtoIdB,
        @Schema(description = "baixa, media, alta ou critica.", example = "critica")
        String severidade,
        @Schema(description = "Risco da mistura.", example = "Libera gás cloro tóxico.")
        String descricaoRisco) {}
