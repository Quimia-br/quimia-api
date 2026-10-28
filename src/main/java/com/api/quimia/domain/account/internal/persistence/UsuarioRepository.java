package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.internal.model.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Outro usuário usa esta foto (`fotoUrl` aceita qualquer URL https, inclusive a de outro perfil). */
    boolean existsByFotoUrlAndIdNot(String fotoUrl, UUID id);
}
