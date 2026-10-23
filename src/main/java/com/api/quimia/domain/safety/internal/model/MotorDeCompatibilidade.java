package com.api.quimia.domain.safety.internal.model;

import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decide a compatibilidade a partir das composições e das regras ativas, sem acesso ao banco. Reproduz
 * `fn_match_produtos` com uma diferença de propósito (R4): composição parcialmente resolvida e sem regra casando é
 * `nao_avaliado`, nunca `compativel`.
 */
public final class MotorDeCompatibilidade {
    public static final String COMPATIVEL = "compativel";
    public static final String INCOMPATIVEL = "incompativel";
    public static final String NAO_AVALIADO = "nao_avaliado";
    public static final String SEGURO = "seguro";
    public static final String ATENCAO = "atencao";
    public static final String CRITICO = "critico";
    public static final String SEM_COMPOSICAO = "Produto sem composição resolvida";
    public static final String COMPOSICAO_INCOMPLETA = "Composição incompleta: há compostos não identificados";

    private static final int MINIMO_PARA_UM_PAR = 2;
    /** Da mais grave para a menos grave; empate pela menor regra. */
    private static final Comparator<RegraDeIncompatibilidade> MAIS_GRAVE_PRIMEIRO = Comparator
            .comparing(RegraDeIncompatibilidade::severidade)
            .reversed()
            .thenComparingInt(RegraDeIncompatibilidade::id);

    private final Map<Integer, ComposicaoDoProduto> composicoes;
    private final List<RegraDeIncompatibilidade> regras;

    /** `regras`: só as ativas. Produto ausente de `composicoes` não tem FDS ativa. */
    public MotorDeCompatibilidade(
            Map<Integer, ComposicaoDoProduto> composicoes, Collection<RegraDeIncompatibilidade> regras) {
        this.composicoes = Map.copyOf(composicoes);
        this.regras = regras.stream().sorted(MAIS_GRAVE_PRIMEIRO).toList();
    }

    /** Par em qualquer ordem; a resposta vem com o menor id em `produtoIdA`. Ids iguais ou nulos não formam par. */
    public AvaliacaoParView par(Integer produtoIdA, Integer produtoIdB) {
        if (produtoIdA == null || produtoIdB == null || produtoIdA.equals(produtoIdB)) {
            throw new IllegalArgumentException("produtos_iguais");
        }
        int menor = Math.min(produtoIdA, produtoIdB);
        int maior = Math.max(produtoIdA, produtoIdB);
        ComposicaoDoProduto a = composicao(menor);
        ComposicaoDoProduto b = composicao(maior);
        if (a.semSubstanciaResolvida() || b.semSubstanciaResolvida()) {
            return new AvaliacaoParView(menor, maior, NAO_AVALIADO, null, SEM_COMPOSICAO, null);
        }
        for (RegraDeIncompatibilidade regra : regras) {
            if (regra.casa(a, b)) {
                return new AvaliacaoParView(
                        menor, maior, INCOMPATIVEL, regra.severidade().codigo(), regra.descricaoRisco(), regra.id());
            }
        }
        if (a.temCompostoNaoResolvido() || b.temCompostoNaoResolvido()) {
            return new AvaliacaoParView(menor, maior, NAO_AVALIADO, null, COMPOSICAO_INCOMPLETA, null);
        }
        return new AvaliacaoParView(menor, maior, COMPATIVEL, null, null, null);
    }

    /**
     * Todos os pares do conjunto (ids nulos ignorados, repetidos contam uma vez). O status é o do pior par:
     * `critico` (alta ou crítica), `atencao` (média ou baixa), `nao_avaliado` (algum par sem avaliação e nenhum
     * incompatível) ou `seguro` (todos compatíveis). Menos de dois produtos não formam par: `nao_avaliado`.
     */
    public AvaliacaoConjuntoView conjunto(Collection<Integer> produtoIds) {
        List<Integer> ids = produtoIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.size() < MINIMO_PARA_UM_PAR) {
            return new AvaliacaoConjuntoView(NAO_AVALIADO, ids.size(), 0, 0, 0, null, List.of());
        }
        List<AvaliacaoParView> incompativeis = new ArrayList<>();
        int avaliados = 0;
        int naoAvaliados = 0;
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                AvaliacaoParView par = par(ids.get(i), ids.get(j));
                if (NAO_AVALIADO.equals(par.resultado())) {
                    naoAvaliados++;
                    continue;
                }
                avaliados++;
                if (INCOMPATIVEL.equals(par.resultado())) {
                    incompativeis.add(par);
                }
            }
        }
        incompativeis.sort(Comparator
                .comparing((AvaliacaoParView par) -> Severidade.doCodigo(par.severidade()))
                .reversed()
                .thenComparing(AvaliacaoParView::regraId)
                .thenComparing(AvaliacaoParView::produtoIdA)
                .thenComparing(AvaliacaoParView::produtoIdB));
        Severidade pior = incompativeis.isEmpty() ? null : Severidade.doCodigo(incompativeis.get(0).severidade());
        return new AvaliacaoConjuntoView(
                status(pior, naoAvaliados),
                ids.size(),
                avaliados,
                incompativeis.size(),
                naoAvaliados,
                pior == null ? null : pior.codigo(),
                List.copyOf(incompativeis));
    }

    private static String status(Severidade pior, int naoAvaliados) {
        if (pior != null) {
            return pior.critica() ? CRITICO : ATENCAO;
        }
        return naoAvaliados > 0 ? NAO_AVALIADO : SEGURO;
    }

    private ComposicaoDoProduto composicao(int produtoId) {
        return composicoes.getOrDefault(produtoId, ComposicaoDoProduto.VAZIA);
    }
}
