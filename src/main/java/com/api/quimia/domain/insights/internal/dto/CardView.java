package com.api.quimia.domain.insights.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** `tipo`: `numero` (usa `valor`) ou `rosca` (usa `series`). */
@Schema(description = "Card de estatística.")
public record CardView(
        @Schema(description = "Identificador estável.", example = "taxa_sucesso_misturas")
        String id,
        @Schema(description = "Título exibido.", example = "Taxa de sucesso")
        String titulo,
        @Schema(description = "numero (usa `valor`) ou rosca (usa `series`).", example = "rosca")
        String tipo,
        @Schema(description = "Valor do card numérico.", example = "128")
        Long valor,
        @Schema(description = "Fatias do gráfico de rosca.")
        List<SerieItem> series) {}
