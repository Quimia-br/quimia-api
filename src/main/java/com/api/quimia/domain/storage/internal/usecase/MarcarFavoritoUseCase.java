package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Favorita ou desfavorita um produto da estante. Idempotente nos dois sentidos. */
@Service
public class MarcarFavoritoUseCase {
    private final EstanteProdutoRepository itens;

    public MarcarFavoritoUseCase(EstanteProdutoRepository itens) {
        this.itens = itens;
    }

    @Transactional
    public void execute(UUID usuarioId, Integer produtoId, boolean favorito) {
        if (itens.marcarFavorito(usuarioId, produtoId, favorito) == 0) {
            throw new StorageException("produto_nao_esta_na_estante", 404);
        }
    }
}
