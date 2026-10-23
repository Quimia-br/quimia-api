package com.api.quimia.domain.recommendation.internal.usecase;

import com.api.quimia.domain.recommendation.internal.persistence.HistoricoMatchRepository;
import com.api.quimia.domain.storage.event.ProdutoRemovidoDaEstanteEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * "Todas as misturas com esse produto serão apagadas". Síncrono e na transação de quem publica:
 * se a remoção falhar depois, a limpeza também é desfeita.
 */
@Component
class LimparMisturasAoRemoverDaEstanteListener {
    private final HistoricoMatchRepository historico;

    LimparMisturasAoRemoverDaEstanteListener(HistoricoMatchRepository historico) {
        this.historico = historico;
    }

    @EventListener
    void aoRemover(ProdutoRemovidoDaEstanteEvent evento) {
        historico.apagarDoUsuarioComProduto(evento.usuarioId(), evento.produtoId());
    }
}
