package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.EmpresaPerfilGateway;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implementação provisória do esqueleto da Fase 2.5: só existe para o contexto subir enquanto o módulo
 * `account` não entrega a implementação real. Quem implementa `EmpresaPerfilGateway` apaga esta classe no mesmo commit.
 */
@Component
class EmpresaPerfilGatewayPendente implements EmpresaPerfilGateway {
    @Override
    public Optional<String> fotoAtual(Integer empresaId) {
        throw naoImplementado();
    }

    @Override
    public void atualizarFoto(Integer empresaId, String fotoUrl) {
        throw naoImplementado();
    }

    private static UnsupportedOperationException naoImplementado() {
        return new UnsupportedOperationException("EmpresaPerfilGateway ainda não foi implementado");
    }
}
