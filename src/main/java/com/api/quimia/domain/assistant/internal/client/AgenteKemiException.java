package com.api.quimia.domain.assistant.internal.client;

/** Falha ao falar com o agente; o motivo decide a resposta ao app e ao portal. */
public class AgenteKemiException extends RuntimeException {
    public enum Motivo {
        /** Sem conexão, agente desligado ou fora do ar (503 do agente). */
        INDISPONIVEL,
        /** O agente não respondeu dentro do prazo. */
        TEMPO_ESGOTADO,
        /** O agente respondeu, mas com erro ou fora do contrato. */
        FALHOU
    }

    private final Motivo motivo;

    public AgenteKemiException(Motivo motivo) {
        super(motivo.name());
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
