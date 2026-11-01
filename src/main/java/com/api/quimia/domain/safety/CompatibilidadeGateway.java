package com.api.quimia.domain.safety;

import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoItensView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import com.api.quimia.domain.safety.dto.ItemParaAvaliar;
import java.util.Collection;

/** Motor de compatibilidade entre produtos e substâncias. Quem chama valida a existência e o acesso aos itens. */
public interface CompatibilidadeGateway {
    /** Avalia um par; os ids podem vir em qualquer ordem. Ids iguais lançam {@code IllegalArgumentException("produtos_iguais")}. */
    AvaliacaoParView avaliarPar(Integer produtoIdA, Integer produtoIdB);

    /**
     * Avalia dois itens, cada um produto ou substância, nas duas ordens. Itens iguais lançam
     * {@code IllegalArgumentException("itens_iguais")}. Uma substância solta só casa com as regras curadas e com as
     * incompatibilidades que a FDS do produto declara contra ela.
     */
    AvaliacaoItensView avaliarItens(ItemParaAvaliar a, ItemParaAvaliar b);

    /** Avalia todos os pares do conjunto; ids repetidos contam uma vez. */
    AvaliacaoConjuntoView avaliarConjunto(Collection<Integer> produtoIds);
}
