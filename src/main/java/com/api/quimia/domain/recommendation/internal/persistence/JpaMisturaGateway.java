package com.api.quimia.domain.recommendation.internal.persistence;

import com.api.quimia.domain.recommendation.MisturaGateway;
import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import java.util.Collection;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaMisturaGateway implements MisturaGateway {
    private final HistoricoMatchRepository historico;

    public JpaMisturaGateway(HistoricoMatchRepository historico) {
        this.historico = historico;
    }

    @Override
    public ResumoMisturasView resumoDosProdutos(Collection<Integer> produtoIds) {
        if (produtoIds.isEmpty()) {
            return new ResumoMisturasView(0, 0, 0, 0);
        }
        return ContagemPorResultado.resumo(historico.contarPorResultado(produtoIds));
    }
}
