package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** `status`: `adicionado`, `ja_na_estante` ou `nao_encontrado`; `nome` vem nulo quando não encontrado. */
@Schema(description = "Resultado de um produto.")
public record ItemGuardado(
        @Schema(description = "Id enviado.", example = "42")
        Integer produtoId,
        @Schema(description = "Nome do produto, quando existe.", example = "Água Sanitária")
        String nome,
        @Schema(description = "adicionado, ja_na_estante ou nao_encontrado.", example = "adicionado")
        String status) {}
