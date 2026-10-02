package com.api.quimia.domain.storage.internal.persistence;

import com.api.quimia.domain.storage.internal.model.Estante;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstanteRepository extends JpaRepository<Estante, Integer> {
    /** Idempotente e seguro com chamadas simultâneas: o UNIQUE(id_usuario) decide quem cria. */
    @Modifying
    @Query(value = "insert into estante (id_usuario, nome) values (:usuario, :nome) on conflict do nothing",
            nativeQuery = true)
    int criarSeNaoExistir(@Param("usuario") UUID usuarioId, @Param("nome") String nome);

    Optional<Estante> findByUsuarioId(UUID usuarioId);
}
