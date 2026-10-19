package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.safety.SafetyGateway;
import com.api.quimia.domain.safety.dto.PrimeirosSocorrosView;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Como se proteger?" só para produtos que estão na estante do próprio usuário. */
@Service
public class ConsultarPrimeirosSocorrosDaEstanteUseCase {
    private final EstanteProdutoRepository itens;
    private final SafetyGateway seguranca;

    public ConsultarPrimeirosSocorrosDaEstanteUseCase(EstanteProdutoRepository itens, SafetyGateway seguranca) {
        this.itens = itens;
        this.seguranca = seguranca;
    }

    @Transactional(readOnly = true)
    public PrimeirosSocorrosView execute(UUID usuarioId, Integer produtoId) {
        if (!itens.produtoIds(usuarioId).contains(produtoId)) {
            throw new StorageException("produto_nao_esta_na_estante", 404);
        }
        return seguranca.primeirosSocorros(produtoId);
    }
}
