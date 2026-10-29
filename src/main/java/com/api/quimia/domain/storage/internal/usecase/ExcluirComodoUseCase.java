package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.storage.internal.model.Comodo;
import com.api.quimia.domain.storage.internal.persistence.ComodoRepository;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Apaga o cômodo; os produtos dele continuam na estante, sem cômodo. */
@Service
public class ExcluirComodoUseCase {
    private final ComodoRepository comodos;
    private final EstanteProdutoRepository itens;

    public ExcluirComodoUseCase(ComodoRepository comodos, EstanteProdutoRepository itens) {
        this.comodos = comodos;
        this.itens = itens;
    }

    @Transactional
    public void execute(UUID usuarioId, Integer comodoId) {
        Comodo comodo = comodos.findByIdAndUsuarioId(comodoId, usuarioId)
                .orElseThrow(() -> new StorageException("comodo_nao_encontrado", 404));
        // O banco já faz ON DELETE SET NULL; soltar antes deixa a regra explícita e vale também no H2.
        itens.soltarDoComodo(usuarioId, comodoId);
        comodos.delete(comodo);
    }
}
