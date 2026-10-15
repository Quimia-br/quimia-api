package com.api.quimia.domain.catalog.internal.image;

/** Falha do armazenamento de imagens; o caso de uso a traduz para o erro HTTP. */
public class ImageStorageException extends RuntimeException {
    public enum Motivo {
        /** Armazenamento sem credenciais configuradas. */
        INDISPONIVEL,
        /** O serviço externo recusou ou não respondeu. */
        FALHOU
    }

    private final Motivo motivo;

    public ImageStorageException(Motivo motivo) {
        super(motivo.name());
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
