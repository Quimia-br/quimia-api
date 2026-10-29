package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.dto.UserView;
import com.api.quimia.domain.account.internal.dto.UpdateProfileRequest;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilUseCase {
    private final UsuarioRepository users;
    private final FotoAnteriorDoUsuario fotoDescartada;

    PerfilUseCase(UsuarioRepository users, FotoAnteriorDoUsuario fotoDescartada) {
        this.users = users;
        this.fotoDescartada = fotoDescartada;
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
            fotoDescartada.apagarAposCommit(id, fotoAnterior, request.fotoUrl());
        }
        return view(user);
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
