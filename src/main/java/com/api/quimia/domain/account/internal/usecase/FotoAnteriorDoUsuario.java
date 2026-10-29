package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.FotoDoUsuarioGateway;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Apaga a foto que o usuário deixou de usar, tanto na troca pelo `PATCH /usuarios/me` quanto no upload pela API. */
@Component
class FotoAnteriorDoUsuario {
    private final UsuarioRepository users;
    private final FotoDoUsuarioGateway fotos;

    FotoAnteriorDoUsuario(UsuarioRepository users, FotoDoUsuarioGateway fotos) {
        this.users = users;
        this.fotos = fotos;
    }

    /**
     * Só depois do commit: se a transação for desfeita, a foto antiga continua sendo a do usuário. `fotoUrl` aceita
     * qualquer URL https, então outro perfil pode apontar para a mesma imagem (copiada de outro usuário): enquanto
     * alguém a usa, ela não é apagada.
     */
    void apagarAposCommit(UUID usuarioId, String anterior, String atual) {
        if (anterior == null || anterior.equals(atual) || users.existsByFotoUrlAndIdNot(anterior, usuarioId)) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fotos.apagar(anterior);
            }
        });
    }
}
