package com.api.quimia.domain.storage.internal.persistence;

import com.api.quimia.domain.storage.EstanteGateway;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementação provisória do esqueleto da Fase 2.5: só existe para o contexto subir enquanto o módulo
 * `storage` não entrega a implementação real. Quem implementa `EstanteGateway` apaga esta classe no mesmo commit.
 */
@Component
class EstanteGatewayPendente implements EstanteGateway {
    @Override
    public List<Integer> produtoIdsDoUsuario(UUID usuarioId) {
        throw naoImplementado();
    }

    @Override
    public long contarGuardados(Collection<Integer> produtoIds) {
        throw naoImplementado();
    }

    private static UnsupportedOperationException naoImplementado() {
        return new UnsupportedOperationException("EstanteGateway ainda não foi implementado");
    }
}
