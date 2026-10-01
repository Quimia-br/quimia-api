package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.internal.model.LocalizacaoUsuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalizacaoUsuarioRepository extends JpaRepository<LocalizacaoUsuario, Integer> {
    Optional<LocalizacaoUsuario> findByUsuarioId(UUID usuarioId);
}
