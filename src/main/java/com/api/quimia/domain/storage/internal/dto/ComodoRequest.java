package com.api.quimia.domain.storage.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** O mínimo de produtos (2) e o máximo (20) são regras do caso de uso, para devolver códigos de erro próprios. */
public record ComodoRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9_-]+") String icone,
        @NotNull List<@NotNull Integer> produtoIds) {}
