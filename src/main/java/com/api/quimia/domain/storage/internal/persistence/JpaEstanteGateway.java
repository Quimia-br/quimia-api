package com.api.quimia.domain.storage.internal.persistence;

import com.api.quimia.domain.storage.EstanteGateway;
import com.api.quimia.domain.storage.internal.model.EstanteProduto;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
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
    @Transactional(propagation = Propagation.MANDATORY)
    public Set<Integer> travarNaEstante(UUID usuarioId, Collection<Integer> produtoIds) {
        if (produtoIds.isEmpty()) {
            return Set.of();
        }
        return itens.travarDoUsuario(usuarioId, produtoIds).stream()
                .map(EstanteProduto::getProdutoId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public long contarGuardados(Collection<Integer> produtoIds) {
        return produtoIds.isEmpty() ? 0 : itens.contarPorProdutos(produtoIds);
    }
}
