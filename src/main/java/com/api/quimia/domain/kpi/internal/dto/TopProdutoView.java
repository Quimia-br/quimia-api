package com.api.quimia.domain.kpi.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Produto da empresa no ranking de consultas. Empates dividem a mesma posição.")
public record TopProdutoView(
        @Schema(description = "Id do produto.", example = "7")
        int idProduto,
        @Schema(description = "Nome do produto.", example = "Desinfetante Pinho")
        String nomeProduto,
        @Schema(description = "Nome da marca.", example = "Marca Exemplo")
        String nomeMarca,
        @Schema(description = "Consultas ao produto.", example = "58")
        long totalConsultas,
        @Schema(description = "Posição no ranking, a partir de 1.", example = "1")
        long ranking) {}
