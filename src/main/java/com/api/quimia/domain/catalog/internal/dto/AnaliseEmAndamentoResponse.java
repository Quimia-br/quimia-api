package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estado de uma análise de FDS feita em segundo plano. O host corta respostas após cerca de 30 s e a leitura de uma
 * FDS real pode passar disso, então o portal envia o PDF, recebe o `id` e consulta até `concluida` ou `falhou`.
 */
@Schema(description = "Estado da análise de FDS em segundo plano.")
public record AnaliseEmAndamentoResponse(
        @Schema(description = "Identificador da análise em andamento; vale 15 minutos depois de terminar.")
        String id,
        @Schema(description = "processando, concluida ou falhou.", example = "processando")
        String status,
        @Schema(description = "Mesmo corpo da análise síncrona; só com `concluida`, senão null.")
        AnaliseDeFdsResponse analise,
        @Schema(description = "Status HTTP que a análise síncrona devolveria; só com `falhou`, senão null.",
                example = "422")
        Integer codigo,
        @Schema(description = "Código do erro (`fds_invalida`, `extrator_falhou`...); só com `falhou`, senão null.",
                example = "fds_invalida")
        String detail) {
    public static final String PROCESSANDO = "processando";
    public static final String CONCLUIDA = "concluida";
    public static final String FALHOU = "falhou";
}
