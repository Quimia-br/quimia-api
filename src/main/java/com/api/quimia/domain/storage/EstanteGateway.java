package com.api.quimia.domain.storage;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contrato público da estante para outros módulos. */
public interface EstanteGateway {
    /** Ids dos produtos na estante do usuário, inclusive os ocultos no catálogo; vazia sem estante. */
    List<Integer> produtoIdsDoUsuario(UUID usuarioId);

    /**
     * Trava (FOR SHARE) os produtos na estante do usuário até o fim da transação de quem chama, que é obrigatória, e
     * devolve os que estão lá. Para gravar algo que só vale enquanto o produto continua na estante: uma remoção
     * concorrente espera o commit e então enxerga o que foi gravado.
     */
    Set<Integer> travarNaEstante(UUID usuarioId, Collection<Integer> produtoIds);

    /** Linhas de `estante_produto` dos produtos informados (uma por usuário que guardou); 0 para lista vazia. */
    long contarGuardados(Collection<Integer> produtoIds);
}
