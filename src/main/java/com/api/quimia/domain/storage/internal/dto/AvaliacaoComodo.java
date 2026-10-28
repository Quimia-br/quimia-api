package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Risco do cômodo, pelo pior par.")
public record AvaliacaoComodo(
        @Schema(
                description = "critico (alta ou crítica), atencao (média ou baixa), nao_avaliado (algum par sem "
                        + "avaliação e nenhum incompatível) ou seguro (todos compatíveis).",
                example = "critico")
        String status,
        @Schema(description = "Produtos avaliados.", example = "4")
        int totalProdutos,
        @Schema(description = "Pares com resultado.", example = "6")
        int paresAvaliados,
        @Schema(description = "Pares incompatíveis.", example = "1")
        int paresIncompativeis,
        @Schema(description = "Severidade do pior par; nula sem incompatível.", example = "critica")
        String severidadeMaxima,
        @Schema(description = "Pares incompatíveis, do mais grave ao menos grave.")
        List<ParIncompativel> incompatibilidades) {}
