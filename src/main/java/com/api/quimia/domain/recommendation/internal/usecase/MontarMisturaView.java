package com.api.quimia.domain.recommendation.internal.usecase;

import com.api.quimia.domain.catalog.CatalogGateway;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.recommendation.internal.dto.MisturaView;
import com.api.quimia.domain.recommendation.internal.dto.SubstanciaDaMistura;
import com.api.quimia.domain.recommendation.internal.model.HistoricoMatch;
import com.api.quimia.domain.safety.SafetyGateway;
import com.api.quimia.domain.safety.dto.SubstanciaView;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Converte linhas de histórico em `MisturaView`, buscando os produtos (inclusive ocultos) em lote e o nome das
 * substâncias soltas.
 */
@Component
class MontarMisturaView {
    private final CatalogGateway catalogo;
    private final SafetyGateway seguranca;

    MontarMisturaView(CatalogGateway catalogo, SafetyGateway seguranca) {
        this.catalogo = catalogo;
        this.seguranca = seguranca;
    }

    MisturaView view(HistoricoMatch historico) {
        return views(List.of(historico)).get(0);
    }

    List<MisturaView> views(List<HistoricoMatch> historicos) {
        if (historicos.isEmpty()) {
            return List.of();
        }
        Set<Integer> produtoIds = new HashSet<>();
        Set<Integer> substanciaIds = new HashSet<>();
        historicos.forEach(historico -> {
            adicionar(produtoIds, historico.getProdutoAId());
            adicionar(produtoIds, historico.getProdutoBId());
            adicionar(substanciaIds, historico.getSubstanciaAId());
            adicionar(substanciaIds, historico.getSubstanciaBId());
        });
        // HashMap e não Map.of(): o lado que é substância consulta o produto com chave nula.
        Map<Integer, ProdutoView> produtos = new HashMap<>();
        if (!produtoIds.isEmpty()) {
            catalogo.listarPorIds(produtoIds, null, 0, produtoIds.size()).itens()
                    .forEach(produto -> produtos.put(produto.id(), produto));
        }
        Map<Integer, SubstanciaDaMistura> substancias = substancias(substanciaIds);
        return historicos.stream()
                .map(historico -> new MisturaView(
                        historico.getId(),
                        produtos.get(historico.getProdutoAId()),
                        produtos.get(historico.getProdutoBId()),
                        substancias.get(historico.getSubstanciaAId()),
                        substancias.get(historico.getSubstanciaBId()),
                        historico.getResultado(),
                        historico.getSeveridade(),
                        historico.getDescricaoRisco(),
                        historico.getDataConsulta(),
                        null))
                .toList();
    }

    private Map<Integer, SubstanciaDaMistura> substancias(Set<Integer> substanciaIds) {
        Map<Integer, SubstanciaDaMistura> substancias = new HashMap<>();
        for (Integer id : substanciaIds) {
            String nome = seguranca.substancia(id).map(SubstanciaView::nome).orElse(null);
            substancias.put(id, new SubstanciaDaMistura(id, nome));
        }
        return substancias;
    }

    private static void adicionar(Set<Integer> ids, Integer id) {
        if (Objects.nonNull(id)) {
            ids.add(id);
        }
    }
}
