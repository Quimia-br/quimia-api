package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.storage.internal.persistence.ComodoRepository;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tira o produto só do cômodo; ele continua na estante. Idempotente. */
@Service
public class RemoverProdutoDoComodoUseCase {
    private final ComodoRepository comodos;
    private final EstanteProdutoRepository itens;

    public RemoverProdutoDoComodoUseCase(ComodoRepository comodos, EstanteProdutoRepository itens) {
        this.comodos = comodos;
        this.itens = itens;
    }

    @Transactional
    public void execute(UUID usuarioId, Integer comodoId, Integer produtoId) {
        comodos.findByIdAndUsuarioId(comodoId, usuarioId)
                .orElseThrow(() -> new StorageException("comodo_nao_encontrado", 404));
        itens.removerDoComodo(usuarioId, comodoId, produtoId);
    }
}
