package com.api.quimia.domain.recommendation.internal.usecase;

import com.api.quimia.domain.recommendation.internal.dto.MisturaView;
import com.api.quimia.domain.recommendation.internal.model.HistoricoMatch;
import com.api.quimia.domain.recommendation.internal.model.ItemDaMistura;
import com.api.quimia.domain.recommendation.internal.persistence.HistoricoMatchRepository;
import com.api.quimia.domain.safety.CompatibilidadeGateway;
import com.api.quimia.domain.safety.SafetyGateway;
import com.api.quimia.domain.safety.dto.AvaliacaoItensView;
import com.api.quimia.domain.safety.dto.ItemParaAvaliar;
import com.api.quimia.domain.safety.dto.SubstanciaNaEstanteView;
import com.api.quimia.domain.storage.EstanteGateway;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cada chamada é uma consulta nova: grava uma linha em `historico_match` com o par normalizado. Cada lado é um produto
 * ou uma substância da estante. Os produtos ficam travados na estante até o commit: se o usuário os remover ao mesmo
 * tempo, a remoção espera e apaga a mistura junto. A substância é a de algum produto da estante e vale como foi
 * lida no momento da consulta.
 */
@Service
public class RegistrarMisturaUseCase {
    private final EstanteGateway estante;
    private final SafetyGateway seguranca;
    private final CompatibilidadeGateway compatibilidade;
    private final HistoricoMatchRepository historico;
    private final MontarMisturaView montar;

    public RegistrarMisturaUseCase(
            EstanteGateway estante,
            SafetyGateway seguranca,
            CompatibilidadeGateway compatibilidade,
            HistoricoMatchRepository historico,
            MontarMisturaView montar) {
        this.estante = estante;
        this.seguranca = seguranca;
        this.compatibilidade = compatibilidade;
        this.historico = historico;
        this.montar = montar;
    }

    @Transactional
    public MisturaView execute(UUID usuarioId, ItemDaMistura itemA, ItemDaMistura itemB) {
        if (itemA.equals(itemB)) {
            throw new RecommendationException(itemA.ehProduto() ? "produtos_iguais" : "substancias_iguais", 422);
        }
        ItemDaMistura primeiro = itemA.compareTo(itemB) <= 0 ? itemA : itemB;
        ItemDaMistura segundo = primeiro == itemA ? itemB : itemA;
        exigirProdutosNaEstante(usuarioId, primeiro, segundo);
        exigirSubstanciasNaEstante(usuarioId, primeiro, segundo);
        AvaliacaoItensView resultado =
                compatibilidade.avaliarItens(paraAvaliar(primeiro), paraAvaliar(segundo));
        HistoricoMatch linha = new HistoricoMatch();
        linha.setUsuarioId(usuarioId);
        linha.setProdutoAId(primeiro.produtoId());
        linha.setSubstanciaAId(primeiro.substanciaId());
        linha.setProdutoBId(segundo.produtoId());
        linha.setSubstanciaBId(segundo.substanciaId());
        linha.setResultado(resultado.resultado());
        linha.setRegraId(resultado.regraId());
        linha.setSeveridade(resultado.severidade());
        linha.setDescricaoRisco(resultado.descricaoRisco());
        linha.setDataConsulta(OffsetDateTime.now());
        return montar.view(historico.save(linha));
    }

    private void exigirProdutosNaEstante(UUID usuarioId, ItemDaMistura primeiro, ItemDaMistura segundo) {
        List<Integer> produtoIds = List.of(primeiro, segundo).stream()
                .filter(ItemDaMistura::ehProduto)
                .map(ItemDaMistura::produtoId)
                .toList();
        if (produtoIds.isEmpty()) {
            return;
        }
        Set<Integer> naEstante = estante.travarNaEstante(usuarioId, produtoIds);
        if (!naEstante.containsAll(produtoIds)) {
            throw new RecommendationException("produto_nao_esta_na_estante", 404);
        }
    }

    /**
     * A substância precisa estar entre as dos produtos da estante (as mesmas de `GET /estante/substancias`) e não pode
     * ser misturada com um produto que já a contém: o par seria dela com ela mesma.
     */
    private void exigirSubstanciasNaEstante(UUID usuarioId, ItemDaMistura primeiro, ItemDaMistura segundo) {
        List<ItemDaMistura> substancias =
                List.of(primeiro, segundo).stream().filter(item -> !item.ehProduto()).toList();
        if (substancias.isEmpty()) {
            return;
        }
        Map<Integer, SubstanciaNaEstanteView> daEstante =
                seguranca.substanciasDosProdutos(estante.produtoIdsDoUsuario(usuarioId)).stream()
                        .collect(Collectors.toMap(SubstanciaNaEstanteView::id, Function.identity(), (a, b) -> a));
        for (ItemDaMistura substancia : substancias) {
            SubstanciaNaEstanteView encontrada = daEstante.get(substancia.substanciaId());
            if (encontrada == null) {
                throw new RecommendationException("substancia_nao_esta_na_estante", 404);
            }
            ItemDaMistura outro = substancia == primeiro ? segundo : primeiro;
            if (outro.ehProduto() && encontrada.produtoIds().contains(outro.produtoId())) {
                throw new RecommendationException("substancia_ja_no_produto", 422);
            }
        }
    }

    private static ItemParaAvaliar paraAvaliar(ItemDaMistura item) {
        return new ItemParaAvaliar(item.produtoId(), item.substanciaId());
    }
}
