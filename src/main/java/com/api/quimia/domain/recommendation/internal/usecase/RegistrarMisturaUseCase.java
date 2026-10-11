package com.api.quimia.domain.recommendation.internal.usecase;

import com.api.quimia.domain.recommendation.internal.dto.MisturaView;
import com.api.quimia.domain.recommendation.internal.model.HistoricoMatch;
import com.api.quimia.domain.recommendation.internal.persistence.HistoricoMatchRepository;
import com.api.quimia.domain.safety.CompatibilidadeGateway;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import com.api.quimia.domain.storage.EstanteGateway;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cada chamada é uma consulta nova: grava uma linha em `historico_match` com o par normalizado. */
@Service
public class RegistrarMisturaUseCase {
    private final EstanteGateway estante;
    private final CompatibilidadeGateway compatibilidade;
    private final HistoricoMatchRepository historico;
    private final MontarMisturaView montar;

    public RegistrarMisturaUseCase(
            EstanteGateway estante,
            CompatibilidadeGateway compatibilidade,
            HistoricoMatchRepository historico,
            MontarMisturaView montar) {
        this.estante = estante;
        this.compatibilidade = compatibilidade;
        this.historico = historico;
        this.montar = montar;
    }

    @Transactional
    public MisturaView execute(UUID usuarioId, Integer produtoIdA, Integer produtoIdB) {
        if (produtoIdA.equals(produtoIdB)) {
            throw new RecommendationException("produtos_iguais", 422);
        }
        Set<Integer> naEstante = Set.copyOf(estante.produtoIdsDoUsuario(usuarioId));
        if (!naEstante.contains(produtoIdA) || !naEstante.contains(produtoIdB)) {
            throw new RecommendationException("produto_nao_esta_na_estante", 404);
        }
        AvaliacaoParView par = compatibilidade.avaliarPar(produtoIdA, produtoIdB);
        HistoricoMatch linha = new HistoricoMatch();
        linha.setUsuarioId(usuarioId);
        linha.setProdutoAId(par.produtoIdA());
        linha.setProdutoBId(par.produtoIdB());
        linha.setResultado(par.resultado());
        linha.setRegraId(par.regraId());
        linha.setSeveridade(par.severidade());
        linha.setDescricaoRisco(par.descricaoRisco());
        linha.setDataConsulta(OffsetDateTime.now());
        return montar.view(historico.save(linha));
    }
}
