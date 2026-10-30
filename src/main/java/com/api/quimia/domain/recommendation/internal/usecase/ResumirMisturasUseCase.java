package com.api.quimia.domain.recommendation.internal.usecase;

import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import com.api.quimia.domain.recommendation.internal.persistence.ContagemPorResultado;
import com.api.quimia.domain.recommendation.internal.persistence.HistoricoMatchRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Totais do histórico do usuário para a Home. Sem cache: cada `POST /misturas` muda a contagem. */
@Service
public class ResumirMisturasUseCase {
    private final HistoricoMatchRepository historico;

    public ResumirMisturasUseCase(HistoricoMatchRepository historico) {
        this.historico = historico;
    }

    @Transactional(readOnly = true)
    public ResumoMisturasView execute(UUID usuarioId) {
        return ContagemPorResultado.resumo(historico.contarDoUsuarioPorResultado(usuarioId));
    }
}
