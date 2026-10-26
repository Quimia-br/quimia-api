package com.api.quimia.domain.catalog.internal.fds;

/** Análise de FDS recusada: o motivo decide o erro devolvido ao portal. */
public class AnaliseDeFdsException extends RuntimeException {
    public enum Motivo {
        /** Assinatura inválida, formato desconhecido ou análise de outra empresa. */
        INVALIDA,
        /** Assinatura válida, mas passou da validade. */
        EXPIRADA,
        /** Correção que não cabe na extração: composto inexistente, repetido, já com unidade ou unidade inválida. */
        CORRECAO_INVALIDA
    }

    private final Motivo motivo;

    public AnaliseDeFdsException(Motivo motivo) {
        super(motivo.name());
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
