package com.api.quimia.domain.recommendation.internal.dto;

import jakarta.validation.constraints.NotNull;

public record MisturaRequest(@NotNull Integer produtoIdA, @NotNull Integer produtoIdB) {}
