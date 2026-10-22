package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Produto da estante que contém a substância.")
public record ProdutoDaSubstancia(
        @Schema(description = "Id do produto.", example = "42")
        Integer id,
        @Schema(description = "Nome do produto.", example = "Água Sanitária Limpa Bem")
        String nome) {}
