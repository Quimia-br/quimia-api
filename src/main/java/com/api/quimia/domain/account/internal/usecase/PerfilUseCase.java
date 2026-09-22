package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.dto.UserView;
import com.api.quimia.domain.account.internal.dto.UpdateProfileRequest;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilUseCase {
    private final UsuarioRepository users;

    public PerfilUseCase(UsuarioRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserView current(UUID id) {
        var user = users.findById(id).orElseThrow(() -> new AccountException("not_found", 404));
        return new UserView(
                user.getId(),
                user.getNome(),
                user.getEmail(),
                user.getDataNasc(),
                user.getNivelAcesso(),
                user.getUltimaSessao());
    }

    @Transactional
    public UserView update(UUID id, UpdateProfileRequest request) {
        var user = users.findById(id).orElseThrow(() -> new AccountException("not_found", 404));
        if (request.nome() != null) {
            user.setNome(request.nome().trim());
        }
        if (request.dataNasc() != null) {
            if (Period.between(request.dataNasc(), LocalDate.now()).getYears() < 18) {
                throw new AccountException("underage", 422);
            }
            user.setDataNasc(request.dataNasc());
        }
        return current(id);
    }
}
