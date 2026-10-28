package com.api.quimia.domain.assistant.internal.client;

import java.util.UUID;

/** Sem `QUIMIA_AGENTE_URL`: o chat responde que o assistente está indisponível. */
public final class AgenteKemiDesligado implements AgenteKemi {
    @Override
    public String perguntar(UUID autorId, String sessaoId, String pergunta) {
        throw new AgenteKemiException(AgenteKemiException.Motivo.INDISPONIVEL);
    }

    @Override
    public String encerrarSessao(UUID autorId, String sessaoId) {
        throw new AgenteKemiException(AgenteKemiException.Motivo.INDISPONIVEL);
    }
}
