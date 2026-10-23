package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.storage.internal.dto.ComodoView;
import com.api.quimia.domain.storage.internal.persistence.ComodoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarComodoUseCase {
    private final ComodoRepository comodos;
    private final MontarComodoView montar;

    public ConsultarComodoUseCase(ComodoRepository comodos, MontarComodoView montar) {
        this.comodos = comodos;
        this.montar = montar;
    }

    /** Cômodo de outro usuário e inexistente dão a mesma resposta (404). */
    @Transactional(readOnly = true)
    public ComodoView execute(UUID usuarioId, Integer comodoId) {
        return comodos.findByIdAndUsuarioId(comodoId, usuarioId)
                .map(montar::view)
                .orElseThrow(() -> new StorageException("comodo_nao_encontrado", 404));
    }
}
