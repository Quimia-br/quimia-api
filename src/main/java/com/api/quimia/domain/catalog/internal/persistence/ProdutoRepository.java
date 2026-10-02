package com.api.quimia.domain.catalog.internal.persistence;

import com.api.quimia.domain.catalog.internal.model.Produto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProdutoRepository extends JpaRepository<Produto, Integer> {
    /**
     * Visível no app: nunca teve vínculo com empresa, ou tem algum vínculo ativo
     * (`ativo` NULL vale como ativo). Some só quando todos os vínculos foram desativados.
     */
    String VISIVEL = """
            (not exists (select 1 from EmpresaProduto ep where ep.produtoId = p.id)
             or exists (select 1 from EmpresaProduto ea
                        where ea.produtoId = p.id and (ea.ativo is null or ea.ativo = true)))
            """;

    String FILTROS = """
            and (:termo is null
                 or lower(unaccent(p.nome)) like :termo escape '!'
                 or lower(unaccent(p.descricao)) like :termo escape '!')
            and (:tipo is null or p.tipo = :tipo)
            and (:codBarras is null or p.codBarras = :codBarras)
            """;

    @Query(value = "select p from Produto p join fetch p.marca where " + VISIVEL + FILTROS,
            countQuery = "select count(p) from Produto p where " + VISIVEL + FILTROS)
    Page<Produto> buscarVisiveis(
            @Param("termo") String termoLike,
            @Param("tipo") String tipo,
            @Param("codBarras") String codBarras,
            Pageable pageable);

    @Query("select p from Produto p join fetch p.marca where p.id = :id and " + VISIVEL)
    Optional<Produto> findVisivelById(@Param("id") Integer id);

    @Query("select p from Produto p join fetch p.marca where p.codBarras in :codigos and " + VISIVEL)
    List<Produto> findVisiveisPorCodigos(@Param("codigos") Collection<String> codigos);
}
