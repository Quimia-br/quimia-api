package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cômodo na lista.")
public record ComodoResumo(
        @Schema(description = "Id do cômodo.", example = "3")
        Integer id,
        @Schema(description = "Nome.", example = "Banheiro")
        String nome,
        @Schema(description = "Ícone.", example = "banheira")
        String icone,
        @Schema(description = "Produtos no cômodo.", example = "4")
        int totalProdutos,
        @Schema(description = "seguro, atencao, critico ou nao_avaliado.", example = "critico")
        String status) {}
