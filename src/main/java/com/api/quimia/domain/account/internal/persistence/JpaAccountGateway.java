package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.account.dto.UserView;
import com.api.quimia.domain.account.internal.model.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaAccountGateway implements AccountGateway {
    private final UsuarioRepository users;

    public JpaAccountGateway(UsuarioRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserView> findById(UUID id) {
        return users.findById(id).map(JpaAccountGateway::view);
    }

    @Override
    public Optional<UserView> findByEmail(String email) {
        return users.findByEmail(normalize(email)).map(JpaAccountGateway::view);
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.existsByEmail(normalize(email));
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static UserView view(Usuario user) {
        return new UserView(
                user.getId(),
                user.getNome(),
                user.getEmail(),
                user.getDataNasc(),
                user.getNivelAcesso(),
                user.getUltimaSessao());
    }
}
