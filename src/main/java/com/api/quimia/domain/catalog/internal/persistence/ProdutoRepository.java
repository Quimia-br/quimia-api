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

    /** O termo chega pronto de {@code TermoBusca.like}. */
    String FILTRO_TERMO = """
             and (:termo is null
                  or lower(unaccent(p.nome)) like :termo escape '!'
                  or lower(unaccent(p.descricao)) like :termo escape '!'
                  or lower(unaccent(p.marca.nome)) like :termo escape '!')
            """;

    String FILTROS_CATALOGO = FILTRO_TERMO + """
             and (:tipo is null or p.tipo = :tipo)
             and (:codBarras is null or p.codBarras = :codBarras)
            """;

    @Query(value = "select p from Produto p join fetch p.marca where " + VISIVEL + FILTROS_CATALOGO,
            countQuery = "select count(p) from Produto p where " + VISIVEL + FILTROS_CATALOGO)
    Page<Produto> buscarVisiveis(
            @Param("termo") String termoLike,
            @Param("tipo") String tipo,
            @Param("codBarras") String codBarras,
            Pageable pageable);

    /** Produtos com vínculo ativo da empresa (portal). */
    String DA_EMPRESA = """
            exists (select 1 from EmpresaProduto ve
                    where ve.produtoId = p.id and ve.empresaId = :empresa and (ve.ativo is null or ve.ativo = true))
            """;

    @Query(value = "select p from Produto p join fetch p.marca where " + DA_EMPRESA + FILTROS_CATALOGO,
            countQuery = "select count(p) from Produto p where " + DA_EMPRESA + FILTROS_CATALOGO)
    Page<Produto> buscarDaEmpresa(
            @Param("empresa") Integer empresaId,
            @Param("termo") String termoLike,
            @Param("tipo") String tipo,
            @Param("codBarras") String codBarras,
            Pageable pageable);

    @Query("select p from Produto p join fetch p.marca where p.id = :id and " + DA_EMPRESA)
    Optional<Produto> findDaEmpresa(@Param("id") Integer id, @Param("empresa") Integer empresaId);

    Optional<Produto> findByCodBarras(String codBarras);

    /** Inclui os produtos sem vínculo ativo: o código continua ocupado no catálogo. */
    List<Produto> findByCodBarrasIn(Collection<String> codigos);

    /**
     * Serializa, até o fim da transação, quem grava códigos equivalentes: o UNIQUE de `cod_barras`
     * só barra o código exato, não o par UPC-A/EAN-13.
     */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:chave)) bloqueio", nativeQuery = true)
    long bloquearCodigo(@Param("chave") long chave);

    @Query("select p from Produto p join fetch p.marca where p.id = :id and " + VISIVEL)
    Optional<Produto> findVisivelById(@Param("id") Integer id);

    @Query("select p from Produto p join fetch p.marca where p.codBarras in :codigos and " + VISIVEL)
    List<Produto> findVisiveisPorCodigos(@Param("codigos") Collection<String> codigos);

    @Query("select p from Produto p join fetch p.marca where p.id in :ids and " + VISIVEL)
    List<Produto> findVisiveisPorIds(@Param("ids") Collection<Integer> ids);

    @Query(value = "select p from Produto p join fetch p.marca where p.id in :ids" + FILTRO_TERMO,
            countQuery = "select count(p) from Produto p where p.id in :ids" + FILTRO_TERMO)
    Page<Produto> buscarPorIds(
            @Param("ids") Collection<Integer> ids, @Param("termo") String termoLike, Pageable pageable);
}
