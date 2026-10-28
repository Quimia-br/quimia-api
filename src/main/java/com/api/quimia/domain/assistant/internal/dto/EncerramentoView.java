package com.api.quimia.domain.assistant.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado de encerrar uma conversa. `situacao`: `resumida` (resumo novo guardado), `ja_resumida` (a conversa já tinha
 * resumo) ou `sem_turnos` (não havia mensagens a resumir).
 */
@Schema(description = "Resultado do encerramento de uma conversa.")
public record EncerramentoView(
        @Schema(description = "Id da conversa encerrada.", example = "b8c0e7f2-1d3a-4f5b-9c6d-2e7f8a9b0c1d")
        String sessaoId,
        @Schema(description = "resumida, ja_resumida ou sem_turnos.", example = "resumida")
        String situacao) {}
