package com.api.quimia.domain.recommendation.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Substância de um dos lados da mistura.")
public record SubstanciaDaMistura(
        @Schema(description = "Id da substância.", example = "7")
        Integer id,
        @Schema(description = "Nome canônico.", example = "Hipoclorito de sódio")
        String nome) {}
