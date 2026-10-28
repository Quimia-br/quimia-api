package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.FotoDoUsuarioGateway;
import com.api.quimia.domain.account.dto.UserView;
import com.api.quimia.domain.account.internal.dto.UpdateProfileRequest;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PerfilUseCase {
    private final UsuarioRepository users;
    private final FotoDoUsuarioGateway fotos;

    public PerfilUseCase(UsuarioRepository users, FotoDoUsuarioGateway fotos) {
        this.users = users;
        this.fotos = fotos;
    }

    @Transactional(readOnly = true)
    public UserView current(UUID id) {
        return view(find(id));
    }

    @Transactional
    public UserView update(UUID id, UpdateProfileRequest request) {
        Usuario user = find(id);
        if (request.nome() != null) {
            user.setNome(request.nome().trim());
        }
        boolean removeDataNasc = Boolean.TRUE.equals(request.removerDataNasc());
        if (removeDataNasc && request.dataNasc() != null) {
            throw new AccountException("data_nasc_conflict", 400);
        }
        if (removeDataNasc) {
            user.setDataNasc(null);
        } else if (request.dataNasc() != null) {
            RegistrarUseCase.requireMinimumAge(request.dataNasc());
            user.setDataNasc(request.dataNasc());
        }
        if (request.fotoUrl() != null) {
            String fotoAnterior = user.getFotoUrl();
            user.setFotoUrl(request.fotoUrl());
            apagarFotoAnteriorAposCommit(id, fotoAnterior, request.fotoUrl());
        }
        return view(user);
    }

    /**
     * Só depois do commit: se a transação for desfeita, a foto antiga continua sendo a do usuário. `fotoUrl` aceita
     * qualquer URL https, então outro perfil pode apontar para a mesma imagem (copiada de outro usuário): enquanto
     * alguém a usa, ela não é apagada.
     */
    private void apagarFotoAnteriorAposCommit(UUID usuarioId, String anterior, String atual) {
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

    private Usuario find(UUID id) {
        return users.findById(id).orElseThrow(() -> new AccountException("not_found", 404));
    }

    private static UserView view(Usuario user) {
        return new UserView(
                user.getId(),
                user.getNome(),
                user.getEmail(),
                user.getDataNasc(),
                user.getFotoUrl(),
                user.getNivelAcesso(),
                user.getUltimaSessao());
    }
}
