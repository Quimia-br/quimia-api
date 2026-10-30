package com.api.quimia.domain.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contagem de misturas por resultado: `compativel`, `incompativel` e `nao_avaliado`. */
@Schema(description = "Contagem das misturas por resultado.")
public record ResumoMisturasView(
        @Schema(description = "Total de misturas.", example = "12")
        long total,
        @Schema(description = "Compatíveis.", example = "7")
        long compativeis,
        @Schema(description = "Incompatíveis.", example = "3")
        long incompativeis,
        @Schema(description = "Não avaliadas (produto sem composição ou com composto não identificado).", example = "2")
        long naoAvaliadas) {}
