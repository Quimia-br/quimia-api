package com.api.quimia.domain.catalog.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuscaCodigoBarrasRequest(@NotBlank @Size(max = 64) String codBarras) {}
