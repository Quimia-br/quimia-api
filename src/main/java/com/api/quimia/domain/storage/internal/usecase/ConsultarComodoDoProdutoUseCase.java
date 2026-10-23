package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.storage.internal.dto.ComodoResumo;
import com.api.quimia.domain.storage.internal.model.EstanteProduto;
import com.api.quimia.domain.storage.internal.persistence.ComodoRepository;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarComodoDoProdutoUseCase {
    private final EstanteProdutoRepository itens;
    private final ComodoRepository comodos;
    private final MontarComodoView montar;

    public ConsultarComodoDoProdutoUseCase(EstanteProdutoRepository itens, ComodoRepository comodos, MontarComodoView montar) {
        this.itens = itens;
        this.comodos = comodos;
        this.montar = montar;
    }

    @Transactional(readOnly = true)
    public ComodoResumo execute(UUID usuarioId, Integer produtoId) {
        EstanteProduto item = itens.doUsuario(usuarioId, produtoId)
                .orElseThrow(() -> new StorageException("produto_nao_esta_na_estante", 404));
        if (item.getComodoId() == null) {
            throw new StorageException("produto_sem_comodo", 404);
        }
        var comodo = comodos.findByIdAndUsuarioId(item.getComodoId(), usuarioId)
                .orElseThrow(() -> new StorageException("produto_sem_comodo", 404));
        return montar.resumo(comodo, montar.produtoIdsDoComodo(usuarioId, comodo.getId()));
    }
}
