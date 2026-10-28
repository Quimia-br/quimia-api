package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** "Guardar" e "Guardar todos" do catálogo: um ou mais produtos de uma vez. */
@Schema(description = "Produtos a guardar.")
public record GuardarProdutosRequest(
        @Schema(description = "De 1 a 50 ids de produto.", example = "[42, 57]")
        @NotEmpty @Size(max = 50) List<@NotNull Integer> produtoIds) {}
