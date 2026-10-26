package com.api.quimia.domain.assistant.internal.client;

import java.util.UUID;

/** O agente Kemi (repositório quimia-agent). */
public interface AgenteKemi {
    /**
     * Texto da resposta à pergunta, na conversa `sessaoId` de `autorId`. Lança {@link AgenteKemiException} quando
     * não há resposta utilizável.
     */
    String perguntar(UUID autorId, String sessaoId, String pergunta);

    /**
     * Encerra a conversa `sessaoId` de `autorId`: o agente resume o que foi dito e guarda o resumo. Devolve
     * {@link #RESUMIDA}, {@link #JA_RESUMIDA} ou {@link #SEM_TURNOS}. Lança {@link AgenteKemiException} quando o agente
     * não responde de forma utilizável.
     */
    String encerrarSessao(UUID autorId, String sessaoId);

    /** Resumo novo guardado. */
    String RESUMIDA = "resumida";
    /** A sessão já tinha resumo; nada foi refeito. */
    String JA_RESUMIDA = "ja_resumida";
    /** A sessão não tinha mensagens a resumir. */
    String SEM_TURNOS = "sem_turnos";
}
