package com.api.quimia.domain.storage.internal.usecase;

import java.util.Set;

/**
 * Ordenações da estante. `favoritos` traz só os favoritos, por nome. Substâncias não têm data nem favorito próprios e
 * só ordenam por nome.
 */
final class OrdemDaEstante {
    static final String ALFABETICA = "alfabetica";
    static final String RECENTES = "recentes";
    static final String ANTIGOS = "antigos";
    static final String FAVORITOS = "favoritos";
    private static final Set<String> DOS_PRODUTOS = Set.of(ALFABETICA, RECENTES, ANTIGOS, FAVORITOS);

    private OrdemDaEstante() {}

    /** Ordem das substâncias da estante: só a alfabética. */
    static void validar(String ordem) {
        if (ordem != null && !ALFABETICA.equals(ordem)) {
            throw new StorageException("ordem_invalida", 400);
        }
    }

    static void validarDosProdutos(String ordem) {
        if (ordem != null && !DOS_PRODUTOS.contains(ordem)) {
            throw new StorageException("ordem_invalida", 400);
        }
    }
}
