package com.api.quimia.domain.assistant.internal.dto;

/**
 * Resultado de encerrar uma conversa. `situacao`: `resumida` (resumo novo guardado), `ja_resumida` (a conversa já tinha
 * resumo) ou `sem_turnos` (não havia mensagens a resumir).
 */
public record EncerramentoView(String sessaoId, String situacao) {}
