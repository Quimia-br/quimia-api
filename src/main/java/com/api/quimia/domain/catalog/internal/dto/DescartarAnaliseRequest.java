package com.api.quimia.domain.catalog.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Análise de FDS que a empresa abandonou; `analiseId` é o devolvido pela análise. */
public record DescartarAnaliseRequest(@NotBlank @Size(max = 262144) String analiseId) {}
