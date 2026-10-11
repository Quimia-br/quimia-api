package com.api.quimia.domain.storage.internal.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProdutosNoComodoRequest(@NotNull List<@NotNull Integer> produtoIds) {}
