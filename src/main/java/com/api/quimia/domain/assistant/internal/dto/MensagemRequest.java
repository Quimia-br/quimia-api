package com.api.quimia.domain.assistant.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mensagem ao Kemi. `sessaoId` agrupa a conversa no agente; sem ele a API abre uma conversa nova e devolve o id na
 * resposta.
 */
public record MensagemRequest(
        @NotBlank @Size(max = 2000) String texto,
        @Size(min = 1, max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String sessaoId) {}
