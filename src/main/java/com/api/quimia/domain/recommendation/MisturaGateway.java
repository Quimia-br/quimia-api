package com.api.quimia.domain.recommendation;

import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import java.util.Collection;

/** Contrato público das misturas para outros módulos. Só agregados, sem identificar usuários. */
public interface MisturaGateway {
    /** Conta as misturas em que o produto A ou o B está na lista; uma mistura conta uma vez. */
    ResumoMisturasView resumoDosProdutos(Collection<Integer> produtoIds);
}
