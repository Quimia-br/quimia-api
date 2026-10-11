package com.api.quimia.domain.storage.internal.usecase;

/** Ordenações da estante. Só a alfabética existe até o DBA entregar data e favoritos (G1/G2). */
final class OrdemDaEstante {
    static final String ALFABETICA = "alfabetica";

    private OrdemDaEstante() {}

    static void validar(String ordem) {
        if (ordem != null && !ALFABETICA.equals(ordem)) {
            throw new StorageException("ordem_invalida", 400);
        }
    }
}
