package com.api.quimia.domain.safety;

import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import java.util.Collection;

/** Motor de compatibilidade entre produtos. Quem chama valida a existência e o acesso aos produtos. */
public interface CompatibilidadeGateway {
    /** Avalia um par; os ids podem vir em qualquer ordem. Ids iguais lançam {@code IllegalArgumentException("produtos_iguais")}. */
    AvaliacaoParView avaliarPar(Integer produtoIdA, Integer produtoIdB);

    /** Avalia todos os pares do conjunto; ids repetidos contam uma vez. */
    AvaliacaoConjuntoView avaliarConjunto(Collection<Integer> produtoIds);
}
