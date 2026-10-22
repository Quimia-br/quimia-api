package com.api.quimia.domain.catalog.internal.persistence;

import com.api.quimia.domain.catalog.internal.model.Produto;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * O termo procurado só nos campos pedidos (`fields`). `:tiposDoTermo` são os códigos de tipo que contêm o termo,
     * calculados por quem chama; o código de barras é comparado como texto, sem normalização.
     */
    String FILTRO_TERMO_POR_CAMPO = """
             and (:termo is null
                  or (:buscaNome = true and lower(unaccent(p.nome)) like :termo escape '!')
                  or (:buscaDescricaoEMarca = true and lower(unaccent(p.descricao)) like :termo escape '!')
                  or (:buscaDescricaoEMarca = true and lower(unaccent(p.marca.nome)) like :termo escape '!')
                  or (:buscaTipo = true and p.tipo in :tiposDoTermo)
                  or (:buscaCodigo = true and p.codBarras like :termo escape '!'))
            """;

    String FILTROS_CATALOGO = FILTRO_TERMO_POR_CAMPO + """
             and (:tipo is null or p.tipo = :tipo)
             and (:semCodigo = true or p.codBarras in :codigos)
            """;

    @Query(value = "select p from Produto p join fetch p.marca where " + VISIVEL + FILTROS_CATALOGO,
            countQuery = "select count(p) from Produto p where " + VISIVEL + FILTROS_CATALOGO)
    Page<Produto> buscarVisiveis(
            @Param("termo") String termoLike,
            @Param("buscaNome") boolean buscaNome,
            @Param("buscaDescricaoEMarca") boolean buscaDescricaoEMarca,
            @Param("buscaTipo") boolean buscaTipo,
            @Param("tiposDoTermo") Collection<String> tiposDoTermo,
            @Param("buscaCodigo") boolean buscaCodigo,
            @Param("tipo") String tipo,
            @Param("semCodigo") boolean semCodigo,
            @Param("codigos") Collection<String> codigos,
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
            @Param("buscaNome") boolean buscaNome,
            @Param("buscaDescricaoEMarca") boolean buscaDescricaoEMarca,
            @Param("buscaTipo") boolean buscaTipo,
            @Param("tiposDoTermo") Collection<String> tiposDoTermo,
            @Param("buscaCodigo") boolean buscaCodigo,
            @Param("tipo") String tipo,
            @Param("semCodigo") boolean semCodigo,
            @Param("codigos") Collection<String> codigos,
            Pageable pageable);

    /** Produtos ativos da empresa do mais recente vínculo para o mais antigo; empate pelo maior id. */
    @Query("""
            select p from Produto p join fetch p.marca
            join EmpresaProduto ep on ep.produtoId = p.id
            where ep.empresaId = :empresa and (ep.ativo is null or ep.ativo = true)
            order by ep.dataCadastro desc nulls last, p.id desc
            """)
    List<Produto> maisRecentesDaEmpresa(@Param("empresa") Integer empresaId, Pageable pageable);

    @Query("select p from Produto p join fetch p.marca where p.id = :id and " + DA_EMPRESA)
    Optional<Produto> findDaEmpresa(@Param("id") Integer id, @Param("empresa") Integer empresaId);

    /**
     * Como {@link #findDaEmpresa}, travando a linha do produto até o fim da transação: alterações simultâneas do
     * mesmo produto (ex.: duas FDS novas) passam uma de cada vez. Sem `join fetch` para não travar a marca.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id = :id and " + DA_EMPRESA)
    Optional<Produto> findDaEmpresaParaAlterar(@Param("id") Integer id, @Param("empresa") Integer empresaId);

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
