package com.api.quimia.domain.recommendation.internal.persistence;

import com.api.quimia.domain.recommendation.internal.model.HistoricoMatch;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoricoMatchRepository extends JpaRepository<HistoricoMatch, Integer> {
    Optional<HistoricoMatch> findByIdAndUsuarioId(Integer id, UUID usuarioId);

    Page<HistoricoMatch> findByUsuarioId(UUID usuarioId, Pageable pageable);

    @Query("""
            select h from HistoricoMatch h
            where h.usuarioId = :usuario and (h.produtoAId = :produto or h.produtoBId = :produto)
            """)
    Page<HistoricoMatch> doUsuarioComProduto(
            @Param("usuario") UUID usuarioId, @Param("produto") Integer produtoId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from HistoricoMatch h
            where h.usuarioId = :usuario and (h.produtoAId = :produto or h.produtoBId = :produto)
            """)
    int apagarDoUsuarioComProduto(@Param("usuario") UUID usuarioId, @Param("produto") Integer produtoId);

    /** Uma linha por resultado; uma mistura conta uma vez mesmo com os dois produtos na lista. */
    @Query("""
            select h.resultado, count(h) from HistoricoMatch h
            where h.produtoAId in :produtos or h.produtoBId in :produtos
            group by h.resultado
            """)
    List<Object[]> contarPorResultado(@Param("produtos") Collection<Integer> produtoIds);
}
