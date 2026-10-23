package com.api.quimia.domain.recommendation.internal.usecase;

import com.api.quimia.domain.catalog.CatalogGateway;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.recommendation.internal.dto.MisturaView;
import com.api.quimia.domain.recommendation.internal.model.HistoricoMatch;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Converte linhas de histórico em `MisturaView`, buscando os produtos (inclusive ocultos) em lote. */
@Component
class MontarMisturaView {
    private final CatalogGateway catalogo;

    MontarMisturaView(CatalogGateway catalogo) {
        this.catalogo = catalogo;
    }

    MisturaView view(HistoricoMatch historico) {
        return views(List.of(historico)).get(0);
    }

    List<MisturaView> views(List<HistoricoMatch> historicos) {
        if (historicos.isEmpty()) {
            return List.of();
        }
        Set<Integer> ids = new HashSet<>();
        historicos.forEach(historico -> {
            ids.add(historico.getProdutoAId());
            ids.add(historico.getProdutoBId());
        });
        Map<Integer, ProdutoView> produtos = catalogo.listarPorIds(ids, null, 0, ids.size()).itens().stream()
                .collect(Collectors.toMap(ProdutoView::id, Function.identity()));
        return historicos.stream()
                .map(historico -> new MisturaView(
                        historico.getId(),
                        produtos.get(historico.getProdutoAId()),
                        produtos.get(historico.getProdutoBId()),
                        historico.getResultado(),
                        historico.getSeveridade(),
                        historico.getDescricaoRisco(),
                        historico.getDataConsulta(),
                        null))
                .toList();
    }
}
