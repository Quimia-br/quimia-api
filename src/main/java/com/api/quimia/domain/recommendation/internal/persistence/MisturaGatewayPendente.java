package com.api.quimia.domain.recommendation.internal.persistence;

import com.api.quimia.domain.recommendation.MisturaGateway;
import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Implementação provisória do esqueleto da Fase 2.5: só existe para o contexto subir enquanto o módulo
 * `recommendation` não entrega a implementação real. Quem implementa `MisturaGateway` apaga esta classe no mesmo commit.
 */
@Component
class MisturaGatewayPendente implements MisturaGateway {
    @Override
    public ResumoMisturasView resumoDosProdutos(Collection<Integer> produtoIds) {
        throw naoImplementado();
    }

    private static UnsupportedOperationException naoImplementado() {
        return new UnsupportedOperationException("MisturaGateway ainda não foi implementado");
    }
}
