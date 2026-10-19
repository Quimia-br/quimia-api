package com.api.quimia.domain.assistant.internal.client;

import java.util.UUID;

/** O agente Kemi (repositório quimia-agent). */
public interface AgenteKemi {
    /**
     * Texto da resposta à pergunta, na conversa `sessaoId` de `autorId`. Lança {@link AgenteKemiException} quando
     * não há resposta utilizável.
     */
    String perguntar(UUID autorId, String sessaoId, String pergunta);
}
