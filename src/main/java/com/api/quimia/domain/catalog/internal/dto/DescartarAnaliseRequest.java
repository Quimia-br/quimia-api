package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Análise de FDS que a empresa abandonou; `analiseId` é o devolvido pela análise. */
@Schema(description = "Análise de FDS que a empresa abandonou.")
public record DescartarAnaliseRequest(
        @Schema(description = "`analiseId` devolvido pela análise.")
        @NotBlank @Size(max = 262144) String analiseId) {}
