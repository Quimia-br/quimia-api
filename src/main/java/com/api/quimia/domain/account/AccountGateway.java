package com.api.quimia.domain.account;

import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.account.dto.UserView;
import java.util.Optional;
import java.util.UUID;

public interface AccountGateway {
    Optional<UserView> findById(UUID id);

    Optional<UserView> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Empresa do portal (titular `EMPRESA`), ativa ou não. */
    Optional<EmpresaView> findEmpresaById(Integer id);
}
