package com.api.quimia.domain.storage.internal.persistence;

import com.api.quimia.domain.storage.internal.model.Comodo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComodoRepository extends JpaRepository<Comodo, Integer> {
    Optional<Comodo> findByIdAndUsuarioId(Integer id, UUID usuarioId);

    List<Comodo> findByUsuarioIdOrderByIdAsc(UUID usuarioId);

    /**
     * Serializa, até o fim da transação, quem cria cômodos com o mesmo nome do mesmo usuário: o banco
     * não tem `UNIQUE (id_usuario, nome)` (G14). No H2 o lock é sem efeito.
     */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:chave)) bloqueio", nativeQuery = true)
    long bloquearNome(@Param("chave") long chave);
}
