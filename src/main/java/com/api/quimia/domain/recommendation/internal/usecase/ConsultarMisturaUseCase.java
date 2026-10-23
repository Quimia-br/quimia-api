package com.api.quimia.domain.recommendation.internal.usecase;

import com.api.quimia.domain.recommendation.internal.dto.MisturaView;
import com.api.quimia.domain.recommendation.internal.persistence.HistoricoMatchRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarMisturaUseCase {
    private final HistoricoMatchRepository historico;
    private final MontarMisturaView montar;

    public ConsultarMisturaUseCase(HistoricoMatchRepository historico, MontarMisturaView montar) {
        this.historico = historico;
        this.montar = montar;
    }

    /** Mistura de outro usuário e inexistente dão a mesma resposta (404). */
    @Transactional(readOnly = true)
    public MisturaView execute(UUID usuarioId, Integer id) {
        return historico.findByIdAndUsuarioId(id, usuarioId)
                .map(montar::view)
                .orElseThrow(() -> new RecommendationException("mistura_nao_encontrada", 404));
    }
}
