package com.api.quimia.domain.catalog.internal.rotulo;

/** Falha ao falar com o agente; o motivo decide a resposta ao app. */
public class LeitorDeRotuloException extends RuntimeException {
    public enum Motivo {
        /** Sem conexão, agente desligado ou fora do ar (503 do agente). */
        INDISPONIVEL,
        /** O agente não respondeu dentro do prazo. */
        TEMPO_ESGOTADO,
        /** O agente respondeu, mas com erro ou fora do contrato. */
        FALHOU
    }

    private final Motivo motivo;

    public LeitorDeRotuloException(Motivo motivo) {
        super(motivo.name());
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
