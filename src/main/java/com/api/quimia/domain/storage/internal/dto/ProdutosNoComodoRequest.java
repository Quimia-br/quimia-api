package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Produtos a adicionar ao cômodo.")
public record ProdutosNoComodoRequest(
        @Schema(description = "Ids de produtos da estante.", example = "[63]")
        @NotNull List<@NotNull Integer> produtoIds) {}
