package com.api.quimia.domain.recommendation.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Dose por produto; sempre nula nesta versão (depende do agente, G9). */
@Schema(description = "Dosagem recomendada (depende do agente; ainda não calculada).")
public record DosagemView(
        @Schema(description = "Dose do produto A.", example = "15 ml")
        String produtoA,
        @Schema(description = "Dose do produto B.", example = "30 ml")
        String produtoB) {}
