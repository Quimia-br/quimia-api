package com.api.quimia.domain.storage.internal.persistence;

import com.api.quimia.domain.storage.internal.model.EstanteProduto;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** Do último guardado ao primeiro; o id desempata os guardados no mesmo instante. */
    @Query("select ep.produtoId from EstanteProduto ep where ep.usuarioId = :usuario order by ep.dataAdicionado desc, ep.id desc")
    List<Integer> produtoIdsDosRecentes(@Param("usuario") UUID usuarioId);

    @Query("select ep.produtoId from EstanteProduto ep where ep.usuarioId = :usuario order by ep.dataAdicionado asc, ep.id asc")
    List<Integer> produtoIdsDosAntigos(@Param("usuario") UUID usuarioId);

    /** Linhas dos produtos na estante do usuário, travadas para leitura (FOR SHARE) até o fim da transação. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select ep from EstanteProduto ep where ep.usuarioId = :usuario and ep.produtoId in :produtos")
    List<EstanteProduto> travarDoUsuario(@Param("usuario") UUID usuarioId, @Param("produtos") Collection<Integer> produtoIds);

    @Modifying
    @Query("delete from EstanteProduto ep where ep.usuarioId = :usuario and ep.produtoId = :produto")
    int remover(@Param("usuario") UUID usuarioId, @Param("produto") Integer produtoId);

    @Query("select ep.produtoId from EstanteProduto ep where ep.usuarioId = :usuario and ep.comodoId = :comodo")
    List<Integer> produtoIdsDoComodo(@Param("usuario") UUID usuarioId, @Param("comodo") Integer comodoId);

    /** Pares (comodoId, produtoId) de todos os cômodos do usuário. */
    @Query("select ep.comodoId, ep.produtoId from EstanteProduto ep where ep.usuarioId = :usuario and ep.comodoId is not null")
    List<Object[]> produtosPorComodo(@Param("usuario") UUID usuarioId);

    @Query("select ep from EstanteProduto ep where ep.usuarioId = :usuario and ep.produtoId = :produto")
    Optional<EstanteProduto> doUsuario(@Param("usuario") UUID usuarioId, @Param("produto") Integer produtoId);

    @Query("select count(ep) from EstanteProduto ep where ep.produtoId in :produtos")
    long contarPorProdutos(@Param("produtos") Collection<Integer> produtoIds);

    /**
     * Liga os produtos ao cômodo se estiverem sem cômodo ou já nele. Devolve quantas linhas mudaram ou já
     * estavam no cômodo; menos que o pedido significa que algum produto está em outro cômodo.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EstanteProduto ep set ep.comodoId = :comodo
            where ep.usuarioId = :usuario and ep.produtoId in :produtos
              and (ep.comodoId is null or ep.comodoId = :comodo)
            """)
    int atribuirComodo(
            @Param("usuario") UUID usuarioId, @Param("produtos") Collection<Integer> produtoIds, @Param("comodo") Integer comodoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EstanteProduto ep set ep.comodoId = null
            where ep.usuarioId = :usuario and ep.comodoId = :comodo and ep.produtoId = :produto
            """)
    int removerDoComodo(@Param("usuario") UUID usuarioId, @Param("comodo") Integer comodoId, @Param("produto") Integer produtoId);

    /** Tira todos os produtos do cômodo; eles continuam na estante. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EstanteProduto ep set ep.comodoId = null where ep.usuarioId = :usuario and ep.comodoId = :comodo")
    int soltarDoComodo(@Param("usuario") UUID usuarioId, @Param("comodo") Integer comodoId);
}
