package com.api.quimia.domain.kpi.internal.persistence;

import com.api.quimia.domain.kpi.internal.dto.ConsultasDiariasView;
import com.api.quimia.domain.kpi.internal.dto.DauDiarioView;
import com.api.quimia.domain.kpi.internal.dto.PerfilUsuariosView;
import com.api.quimia.domain.kpi.internal.dto.TopProdutoView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas dos KPIs da empresa sobre as views do data mart. Todas partem dos produtos da empresa em
 * {@code empresa_produto} (vínculo {@code ativo} ou {@code NULL}, como no resto da API) e recebem o id da empresa como
 * parâmetro: o filtro por empresa não existe fora destas consultas.
 */
@Repository
public class KpiEmpresaRepository {
    private static final String DAU = """
            WITH base AS (
              SELECT h.data AS data, COUNT(DISTINCT h.id_usuario) AS usuarios_ativos
              FROM vw_fato_historico_recomendacao h
              JOIN empresa_produto ep ON ep.id_produto = h.id_produto
              WHERE ep.id_empresa = :empresa AND (ep.ativo IS NULL OR ep.ativo)
              GROUP BY h.data
            )
            SELECT data, usuarios_ativos,
              usuarios_ativos - LAG(usuarios_ativos) OVER (ORDER BY data) AS variacao_usuarios,
              ROUND((usuarios_ativos - LAG(usuarios_ativos) OVER (ORDER BY data)) * 100.0
                    / NULLIF(LAG(usuarios_ativos) OVER (ORDER BY data), 0), 2) AS variacao_pct,
              AVG(usuarios_ativos) OVER () AS media_global,
              AVG(usuarios_ativos) OVER (ORDER BY data ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS media_7d,
              STDDEV_SAMP(usuarios_ativos) OVER () AS desvio_padrao,
              ROUND((usuarios_ativos - AVG(usuarios_ativos) OVER ())
                    / NULLIF(STDDEV_SAMP(usuarios_ativos) OVER (), 0), 2) AS z_score
            FROM base
            ORDER BY data
            """;

    private static final String CONSULTAS_POR_DIA = """
            SELECT h.data AS data, COUNT(h.id_fato) AS total_consultas, COUNT(DISTINCT h.id_usuario) AS usuarios_distintos
            FROM vw_fato_historico_recomendacao h
            JOIN empresa_produto ep ON ep.id_produto = h.id_produto
            WHERE ep.id_empresa = :empresa AND (ep.ativo IS NULL OR ep.ativo)
            GROUP BY h.data
            ORDER BY h.data
            """;

    private static final String TOP_PRODUTOS = """
            SELECT h.id_produto, h.nome_produto, h.nome_marca, COUNT(h.id_fato) AS total_consultas,
                   RANK() OVER (ORDER BY COUNT(h.id_fato) DESC) AS ranking
            FROM vw_fato_historico_recomendacao h
            JOIN empresa_produto ep ON ep.id_produto = h.id_produto
            WHERE ep.id_empresa = :empresa AND (ep.ativo IS NULL OR ep.ativo)
            GROUP BY h.id_produto, h.nome_produto, h.nome_marca
            ORDER BY ranking, h.nome_produto, h.id_produto
            LIMIT :limite
            """;

    private static final String PERFIL_USUARIOS = """
            SELECT u.nivel_acesso, COALESCE(u.estado, 'NI') AS estado, COUNT(DISTINCT h.id_usuario) AS total_usuarios
            FROM vw_fato_historico_recomendacao h
            JOIN empresa_produto ep ON ep.id_produto = h.id_produto
            JOIN dim_usuario u ON u.id_usuario = h.id_usuario
            WHERE ep.id_empresa = :empresa AND (ep.ativo IS NULL OR ep.ativo)
            GROUP BY u.nivel_acesso, COALESCE(u.estado, 'NI')
            ORDER BY total_usuarios DESC, u.nivel_acesso, estado
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public KpiEmpresaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DauDiarioView> dau(Integer empresaId) {
        return jdbc.query(DAU, new MapSqlParameterSource("empresa", empresaId), (rs, linha) -> new DauDiarioView(
                rs.getObject("data", LocalDate.class),
                rs.getLong("usuarios_ativos"),
                longOuNulo(rs, "variacao_usuarios"),
                doubleOuNulo(rs, "variacao_pct"),
                doubleOuNulo(rs, "media_global"),
                doubleOuNulo(rs, "media_7d"),
                doubleOuNulo(rs, "desvio_padrao"),
                doubleOuNulo(rs, "z_score")));
    }

    public List<ConsultasDiariasView> consultasPorDia(Integer empresaId) {
        return jdbc.query(
                CONSULTAS_POR_DIA,
                new MapSqlParameterSource("empresa", empresaId),
                (rs, linha) -> new ConsultasDiariasView(
                        rs.getObject("data", LocalDate.class),
                        rs.getLong("total_consultas"),
                        rs.getLong("usuarios_distintos")));
    }

    public List<TopProdutoView> topProdutos(Integer empresaId, int limite) {
        MapSqlParameterSource parametros =
                new MapSqlParameterSource("empresa", empresaId).addValue("limite", limite);
        return jdbc.query(TOP_PRODUTOS, parametros, (rs, linha) -> new TopProdutoView(
                rs.getInt("id_produto"),
                rs.getString("nome_produto"),
                rs.getString("nome_marca"),
                rs.getLong("total_consultas"),
                rs.getLong("ranking")));
    }

    public List<PerfilUsuariosView> perfilUsuarios(Integer empresaId) {
        return jdbc.query(
                PERFIL_USUARIOS,
                new MapSqlParameterSource("empresa", empresaId),
                (rs, linha) -> new PerfilUsuariosView(
                        rs.getString("nivel_acesso"), rs.getString("estado"), rs.getLong("total_usuarios")));
    }

    private static Long longOuNulo(ResultSet rs, String coluna) throws SQLException {
        long valor = rs.getLong(coluna);
        return rs.wasNull() ? null : valor;
    }

    private static Double doubleOuNulo(ResultSet rs, String coluna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(coluna);
        return valor == null ? null : valor.doubleValue();
    }
}
