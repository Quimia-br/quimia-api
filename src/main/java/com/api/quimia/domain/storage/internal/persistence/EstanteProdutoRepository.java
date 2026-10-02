package com.api.quimia.domain.storage.internal.persistence;

import com.api.quimia.domain.storage.internal.model.EstanteProduto;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstanteProdutoRepository extends JpaRepository<EstanteProduto, Integer> {
    /** Retorna 1 quando guardou e 0 quando o produto já estava na estante (UNIQUE(id_estante, id_produto)). */
    @Modifying
    @Query(value = """
            insert into estante_produto (id_produto, id_usuario, id_estante)
            values (:produto, :usuario, :estante)
            on conflict do nothing
            """, nativeQuery = true)
    int guardar(@Param("produto") Integer produtoId, @Param("usuario") UUID usuarioId, @Param("estante") Integer estanteId);

    @Query("select ep.produtoId from EstanteProduto ep where ep.usuarioId = :usuario")
    List<Integer> produtoIds(@Param("usuario") UUID usuarioId);

    @Modifying
    @Query("delete from EstanteProduto ep where ep.usuarioId = :usuario and ep.produtoId = :produto")
    int remover(@Param("usuario") UUID usuarioId, @Param("produto") Integer produtoId);
}
