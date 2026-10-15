package com.api.quimia.domain.storage.internal.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** "Guardar" e "Guardar todos" do catálogo: um ou mais produtos de uma vez. */
public record GuardarProdutosRequest(@NotEmpty @Size(max = 50) List<@NotNull Integer> produtoIds) {}
