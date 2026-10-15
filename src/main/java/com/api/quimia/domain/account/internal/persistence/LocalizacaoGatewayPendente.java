package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.LocalizacaoGateway;
import com.api.quimia.domain.account.dto.LocalizacaoView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementação provisória do esqueleto da Fase 2.5: só existe para o contexto subir enquanto o módulo
 * `account` não entrega a implementação real. Quem implementa `LocalizacaoGateway` apaga esta classe no mesmo commit.
 */
@Component
class LocalizacaoGatewayPendente implements LocalizacaoGateway {
    @Override
    public Optional<LocalizacaoView> doUsuario(UUID usuarioId) {
        throw naoImplementado();
    }

    private static UnsupportedOperationException naoImplementado() {
        return new UnsupportedOperationException("LocalizacaoGateway ainda não foi implementado");
    }
}
