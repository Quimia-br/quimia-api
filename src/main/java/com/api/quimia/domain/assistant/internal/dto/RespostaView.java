package com.api.quimia.domain.assistant.internal.dto;

import java.time.OffsetDateTime;

/** Resposta do Kemi; `sessaoId` é o da conversa, para o próximo envio. */
public record RespostaView(String texto, String sessaoId, OffsetDateTime respondidoEm) {}
