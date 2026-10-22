package com.api.quimia.domain.storage.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Cômodo com produtos e avaliação de risco.")
public record ComodoView(
        @Schema(description = "Id do cômodo.", example = "3")
        Integer id,
        @Schema(description = "Nome.", example = "Banheiro")
        String nome,
        @Schema(description = "Ícone.", example = "banheira")
        String icone,
        @Schema(description = "Risco de todos os pares do cômodo.")
        AvaliacaoComodo avaliacao,
        @Schema(description = "Produtos, por nome.")
        List<ProdutoView> produtos) {}
