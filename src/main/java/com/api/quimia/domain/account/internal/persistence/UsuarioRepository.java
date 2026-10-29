package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.internal.model.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Outro usuário usa esta foto (`fotoUrl` aceita qualquer URL https, inclusive a de outro perfil). */
    boolean existsByFotoUrlAndIdNot(String fotoUrl, UUID id);

    /** Só a coluna da foto, sem carregar a entidade: a gravação seguinte não regrava a linha inteira. */
    @Query("select usuario.fotoUrl from Usuario usuario where usuario.id = :id")
    Optional<String> findFotoUrlById(@Param("id") UUID id);

    /** Atualiza só `foto_url`; senha, bloqueio e demais colunas alteradas por outra transação ficam intactas. */
    @Modifying
    @Query("update Usuario usuario set usuario.fotoUrl = :fotoUrl where usuario.id = :id")
    int atualizarFotoUrl(@Param("id") UUID id, @Param("fotoUrl") String fotoUrl);
}
