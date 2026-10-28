package com.api.quimia.domain.assistant.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** Resposta do Kemi; `sessaoId` é o da conversa, para o próximo envio. */
@Schema(description = "Resposta do assistente.")
public record RespostaView(
        @Schema(description = "Resposta.", example = "Não misture: a reação libera gás tóxico.")
        String texto,
        @Schema(
                description = "Id da conversa, para a próxima mensagem.",
                example = "b8c0e7f2-1d3a-4f5b-9c6d-2e7f8a9b0c1d")
        String sessaoId,
        @Schema(description = "Momento da resposta.", example = "2026-10-22T10:15:30-03:00")
        OffsetDateTime respondidoEm) {}
