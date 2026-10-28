package com.api.quimia.domain.assistant.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mensagem ao Kemi. `sessaoId` agrupa a conversa no agente; sem ele a API abre uma conversa nova e devolve o id na
 * resposta.
 */
@Schema(description = "Mensagem ao assistente.")
public record MensagemRequest(
        @Schema(
                description = "Pergunta, até 2000 caracteres.",
                example = "Posso misturar água sanitária com desinfetante?")
        @NotBlank @Size(max = 2000) String texto,
        @Schema(
                description = "Conversa a continuar: letras, dígitos, _ e -, até 64. Sem ele a API abre uma "
                        + "conversa nova.",
                example = "b8c0e7f2-1d3a-4f5b-9c6d-2e7f8a9b0c1d")
        @Size(min = 1, max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String sessaoId) {}
