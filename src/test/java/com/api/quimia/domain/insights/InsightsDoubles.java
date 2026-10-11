package com.api.quimia.domain.insights;

import com.api.quimia.domain.catalog.CatalogEmpresaGateway;
import com.api.quimia.domain.catalog.dto.MarcaView;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.recommendation.MisturaGateway;
import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import com.api.quimia.domain.storage.EstanteGateway;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Substitui `catalog`, `storage` e `recommendation`, ainda não integrados neste worktree. Lê o banco por JDBC. */
@TestConfiguration
public class InsightsDoubles {
    private static String marcadores(Collection<Integer> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    @Bean
    @Primary
    CatalogEmpresaGateway catalogoDuble(JdbcTemplate jdbc) {
        return new CatalogEmpresaGateway() {
            @Override
            public List<Integer> produtoIdsAtivos(Integer empresaId) {
                return jdbc.queryForList(
                        "SELECT id_produto FROM empresa_produto WHERE id_empresa = ? AND (ativo IS NULL OR ativo = TRUE)",
                        Integer.class, empresaId);
            }

            @Override
            public Optional<ProdutoView> produtoMaisRecente(Integer empresaId) {
                return jdbc.query(
                                """
                                SELECT p.id, p.nome, p.tipo_produto, p.descricao, p.cod_barras, p.foto_url, m.id AS marca_id,
                                       m.nome AS marca_nome, m.logo_url
                                FROM empresa_produto ep
                                JOIN produto p ON p.id = ep.id_produto
                                JOIN marca m ON m.id = p.id_marca
                                WHERE ep.id_empresa = ? AND (ep.ativo IS NULL OR ep.ativo = TRUE)
                                ORDER BY ep.data_cadastro DESC NULLS LAST, p.id DESC
                                LIMIT 1
                                """,
                                (rs, i) -> new ProdutoView(
                                        rs.getInt("id"), rs.getString("nome"), rs.getString("tipo_produto"),
                                        rs.getString("descricao"), rs.getString("cod_barras"), rs.getString("foto_url"),
                                        new MarcaView(rs.getInt("marca_id"), rs.getString("marca_nome"), rs.getString("logo_url"))),
                                empresaId)
                        .stream()
                        .findFirst();
            }
        };
    }

    @Bean
    @Primary
    EstanteGateway estanteDuble(JdbcTemplate jdbc) {
        return new EstanteGateway() {
            @Override
            public List<Integer> produtoIdsDoUsuario(UUID usuarioId) {
                throw new UnsupportedOperationException("não usado por insights");
            }

            @Override
            public long contarGuardados(Collection<Integer> produtoIds) {
                if (produtoIds.isEmpty()) {
                    return 0;
                }
                Long total = jdbc.queryForObject(
                        "SELECT count(*) FROM estante_produto WHERE id_produto IN (" + marcadores(produtoIds) + ")",
                        Long.class, produtoIds.toArray());
                return total == null ? 0 : total;
            }
        };
    }

    @Bean
    @Primary
    MisturaGateway misturaDuble(JdbcTemplate jdbc) {
        return produtoIds -> {
            if (produtoIds.isEmpty()) {
                return new ResumoMisturasView(0, 0, 0, 0);
            }
            String in = marcadores(produtoIds);
            Object[] ids = produtoIds.toArray();
            Object[] parametros = new Object[ids.length * 2];
            System.arraycopy(ids, 0, parametros, 0, ids.length);
            System.arraycopy(ids, 0, parametros, ids.length, ids.length);
            long[] contagem = new long[3];
            jdbc.query(
                    "SELECT resultado, count(*) AS n FROM historico_match WHERE id_produto_a IN (" + in
                            + ") OR id_produto_b IN (" + in + ") GROUP BY resultado",
                    rs -> {
                        long n = rs.getLong("n");
                        switch (rs.getString("resultado")) {
                            case "compativel" -> contagem[0] = n;
                            case "incompativel" -> contagem[1] = n;
                            default -> contagem[2] += n;
                        }
                    },
                    parametros);
            return new ResumoMisturasView(contagem[0] + contagem[1] + contagem[2], contagem[0], contagem[1], contagem[2]);
        };
    }
}
