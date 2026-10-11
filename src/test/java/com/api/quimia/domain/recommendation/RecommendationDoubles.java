package com.api.quimia.domain.recommendation;

import com.api.quimia.domain.safety.CompatibilidadeGateway;
import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import com.api.quimia.domain.storage.EstanteGateway;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Substitui `safety` e `storage`, ainda não integrados neste worktree. */
@TestConfiguration
public class RecommendationDoubles {
    /** Resultado programado pelo teste para o próximo `avaliarPar`. */
    public static volatile String resultado = "compativel";
    public static volatile String severidade = null;
    public static volatile String descricao = null;
    public static volatile Integer regraId = null;

    public static void programar(String novoResultado, String novaSeveridade, String novaDescricao, Integer novaRegra) {
        resultado = novoResultado;
        severidade = novaSeveridade;
        descricao = novaDescricao;
        regraId = novaRegra;
    }

    @Bean
    @Primary
    CompatibilidadeGateway compatibilidadeDuble() {
        return new CompatibilidadeGateway() {
            @Override
            public AvaliacaoParView avaliarPar(Integer a, Integer b) {
                return new AvaliacaoParView(Math.min(a, b), Math.max(a, b), resultado, severidade, descricao, regraId);
            }

            @Override
            public AvaliacaoConjuntoView avaliarConjunto(Collection<Integer> produtoIds) {
                throw new UnsupportedOperationException("não usado por recommendation");
            }
        };
    }

    /** Lê a estante direto do banco, como o gateway real de `storage` fará. */
    @Bean
    @Primary
    EstanteGateway estanteDuble(JdbcTemplate jdbc) {
        return new EstanteGateway() {
            @Override
            public List<Integer> produtoIdsDoUsuario(UUID usuarioId) {
                return jdbc.queryForList("SELECT id_produto FROM estante_produto WHERE id_usuario = ?", Integer.class, usuarioId);
            }

            @Override
            public long contarGuardados(Collection<Integer> produtoIds) {
                throw new UnsupportedOperationException("não usado por recommendation");
            }
        };
    }
}
