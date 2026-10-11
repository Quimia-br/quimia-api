package com.api.quimia.domain.storage;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Contrato público da estante para outros módulos. */
public interface EstanteGateway {
    /** Ids dos produtos na estante do usuário, inclusive os ocultos no catálogo; vazia sem estante. */
    List<Integer> produtoIdsDoUsuario(UUID usuarioId);

    /** Linhas de `estante_produto` dos produtos informados (uma por usuário que guardou); 0 para lista vazia. */
    long contarGuardados(Collection<Integer> produtoIds);
}
