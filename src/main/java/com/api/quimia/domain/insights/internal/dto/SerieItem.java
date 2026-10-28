package com.api.quimia.domain.insights.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fatia de gráfico.")
public record SerieItem(
        @Schema(description = "Rótulo.", example = "Bem sucedida")
        String rotulo,
        @Schema(description = "Valor.", example = "30")
        long valor) {}
