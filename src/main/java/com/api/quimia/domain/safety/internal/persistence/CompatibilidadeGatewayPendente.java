package com.api.quimia.domain.safety.internal.persistence;

import com.api.quimia.domain.safety.CompatibilidadeGateway;
import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Implementação provisória do esqueleto da Fase 2.5: só existe para o contexto subir enquanto o módulo
 * `safety` não entrega a implementação real. Quem implementa `CompatibilidadeGateway` apaga esta classe no mesmo commit.
 */
@Component
class CompatibilidadeGatewayPendente implements CompatibilidadeGateway {
    @Override
    public AvaliacaoParView avaliarPar(Integer produtoIdA, Integer produtoIdB) {
        throw naoImplementado();
    }

    @Override
    public AvaliacaoConjuntoView avaliarConjunto(Collection<Integer> produtoIds) {
        throw naoImplementado();
    }

    private static UnsupportedOperationException naoImplementado() {
        return new UnsupportedOperationException("CompatibilidadeGateway ainda não foi implementado");
    }
}
