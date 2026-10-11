package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.catalog.CatalogGateway;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.safety.CompatibilidadeGateway;
import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.storage.internal.dto.AvaliacaoComodo;
import com.api.quimia.domain.storage.internal.dto.ComodoResumo;
import com.api.quimia.domain.storage.internal.dto.ComodoView;
import com.api.quimia.domain.storage.internal.dto.ParIncompativel;
import com.api.quimia.domain.storage.internal.model.Comodo;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Monta as visões de cômodo: produtos (inclusive ocultos no catálogo) e avaliação de risco. */
@Component
class MontarComodoView {
    private final EstanteProdutoRepository itens;
    private final CatalogGateway catalogo;
    private final CompatibilidadeGateway compatibilidade;

    MontarComodoView(EstanteProdutoRepository itens, CatalogGateway catalogo, CompatibilidadeGateway compatibilidade) {
        this.itens = itens;
        this.catalogo = catalogo;
        this.compatibilidade = compatibilidade;
    }

    ComodoView view(Comodo comodo) {
        List<Integer> produtoIds = itens.produtoIdsDoComodo(comodo.getUsuarioId(), comodo.getId());
        List<ProdutoView> produtos = produtoIds.isEmpty()
                ? List.of()
                : catalogo.listarPorIds(produtoIds, null, 0, produtoIds.size()).itens().stream()
                        .sorted(Comparator.comparing((ProdutoView produto) -> TextoBusca.normalizar(produto.nome()))
                                .thenComparing(ProdutoView::id))
                        .toList();
        return new ComodoView(comodo.getId(), comodo.getNome(), comodo.getIcone(), avaliacao(produtoIds), produtos);
    }

    ComodoResumo resumo(Comodo comodo, List<Integer> produtoIds) {
        return new ComodoResumo(
                comodo.getId(), comodo.getNome(), comodo.getIcone(), produtoIds.size(), avaliacao(produtoIds).status());
    }

    AvaliacaoComodo avaliacao(List<Integer> produtoIds) {
        AvaliacaoConjuntoView conjunto = compatibilidade.avaliarConjunto(produtoIds);
        List<ParIncompativel> incompativeis = conjunto.incompatibilidades().stream()
                .map(par -> new ParIncompativel(par.produtoIdA(), par.produtoIdB(), par.severidade(), par.descricaoRisco()))
                .toList();
        return new AvaliacaoComodo(
                conjunto.status(),
                conjunto.totalProdutos(),
                conjunto.paresAvaliados(),
                conjunto.paresIncompativeis(),
                conjunto.severidadeMaxima(),
                incompativeis);
    }

    List<Integer> produtoIdsDoComodo(UUID usuarioId, Integer comodoId) {
        return itens.produtoIdsDoComodo(usuarioId, comodoId);
    }
}
