package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.internal.model.Usuario;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from Usuario user where user.id = :id")
    Optional<Usuario> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from Usuario user where user.email = :email")
    Optional<Usuario> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    Optional<Usuario> findByVerificationTokenHash(String verificationTokenHash);
}
