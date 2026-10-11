package com.api.quimia.domain.storage.internal.persistence;

import com.api.quimia.domain.storage.EstanteGateway;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaEstanteGateway implements EstanteGateway {
    private final EstanteProdutoRepository itens;

    public JpaEstanteGateway(EstanteProdutoRepository itens) {
        this.itens = itens;
    }

    @Override
    public List<Integer> produtoIdsDoUsuario(UUID usuarioId) {
        return itens.produtoIds(usuarioId);
    }

    @Override
    public long contarGuardados(Collection<Integer> produtoIds) {
        return produtoIds.isEmpty() ? 0 : itens.contarPorProdutos(produtoIds);
    }
}
