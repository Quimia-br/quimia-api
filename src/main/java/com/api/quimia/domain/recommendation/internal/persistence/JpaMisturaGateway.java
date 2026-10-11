package com.api.quimia.domain.recommendation.internal.persistence;

import com.api.quimia.domain.recommendation.MisturaGateway;
import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import java.util.Collection;
import java.util.List;
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
        long compativeis = 0;
        long incompativeis = 0;
        long naoAvaliadas = 0;
        List<Object[]> linhas = historico.contarPorResultado(produtoIds);
        for (Object[] linha : linhas) {
            long quantidade = ((Number) linha[1]).longValue();
            switch ((String) linha[0]) {
                case "compativel" -> compativeis = quantidade;
                case "incompativel" -> incompativeis = quantidade;
                default -> naoAvaliadas += quantidade;
            }
        }
        return new ResumoMisturasView(compativeis + incompativeis + naoAvaliadas, compativeis, incompativeis, naoAvaliadas);
    }
}
