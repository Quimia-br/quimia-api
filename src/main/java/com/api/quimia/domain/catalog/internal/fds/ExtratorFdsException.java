package com.api.quimia.domain.catalog.internal.fds;

/** Falha ao extrair a FDS; o motivo decide o erro devolvido ao portal. */
public class ExtratorFdsException extends RuntimeException {
    public enum Motivo {
        /** Sem URL configurada, conexão recusada ou sem resposta no prazo. */
        INDISPONIVEL,
        /** O extrator respondeu erro, token recusado ou JSON fora do contrato. */
        FALHOU,
        /** O documento não é uma FDS ou não tem texto legível. */
        DOCUMENTO_RECUSADO,
        /** O extrator recusou o tamanho do arquivo. */
        MUITO_GRANDE
    }

    private final Motivo motivo;

    public ExtratorFdsException(Motivo motivo) {
        super(motivo.name());
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
