package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.storage.event.ProdutoRemovidoDaEstanteEvent;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotente: remover um produto que não está na estante não é erro e não publica evento. */
@Service
public class RemoverDaEstanteUseCase {
    private final EstanteProdutoRepository itens;
    private final ApplicationEventPublisher events;

    public RemoverDaEstanteUseCase(EstanteProdutoRepository itens, ApplicationEventPublisher events) {
        this.itens = itens;
        this.events = events;
    }

    @Transactional
    public void execute(UUID usuarioId, Integer produtoId) {
        if (itens.remover(usuarioId, produtoId) > 0) {
            events.publishEvent(new ProdutoRemovidoDaEstanteEvent(usuarioId, produtoId));
        }
    }
}
