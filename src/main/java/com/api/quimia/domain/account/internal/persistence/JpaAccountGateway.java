package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.account.dto.UserView;
import com.api.quimia.domain.account.internal.model.Empresa;
import com.api.quimia.domain.account.internal.model.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaAccountGateway implements AccountGateway {
    private final UsuarioRepository users;
    private final EmpresaRepository empresas;

    public JpaAccountGateway(UsuarioRepository users, EmpresaRepository empresas) {
        this.users = users;
        this.empresas = empresas;
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

    @Override
    public Optional<EmpresaView> findEmpresaById(Integer id) {
        return empresas.findById(id).map(JpaAccountGateway::view);
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
                user.getFotoUrl(),
                user.getNivelAcesso(),
                user.getUltimaSessao());
    }

    private static EmpresaView view(Empresa empresa) {
        return new EmpresaView(
                empresa.getId(),
                empresa.getNome(),
                empresa.getEmail(),
                empresa.getCnpj(),
                empresa.getFotoUrl(),
                empresa.isAtiva());
    }
}
