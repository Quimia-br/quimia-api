package com.api.quimia.domain.insights.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Tela Estatísticas.")
public record EstatisticasView(
        @Schema(description = "Cards, na ordem da tela.")
        List<CardView> cards) {}
