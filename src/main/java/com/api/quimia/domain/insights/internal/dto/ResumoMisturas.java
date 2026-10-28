package com.api.quimia.domain.insights.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** `taxaSucesso` = bemSucedidas / (bemSucedidas + malSucedidas), 4 casas; nula sem denominador. */
@Schema(description = "Misturas com produtos da empresa.")
public record ResumoMisturas(
        @Schema(description = "Total de misturas.", example = "40")
        long total,
        @Schema(description = "Compatíveis.", example = "30")
        long bemSucedidas,
        @Schema(description = "Incompatíveis.", example = "6")
        long malSucedidas,
        @Schema(description = "Não avaliadas.", example = "4")
        long naoAvaliadas,
        @Schema(
                description = "Compatíveis sobre avaliadas, de 0 a 1 com 4 casas; nula sem avaliadas.",
                example = "0.8333")
        Double taxaSucesso) {}
