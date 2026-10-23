package com.api.quimia.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.storage.event.ProdutoRemovidoDaEstanteEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class LimpezaDoHistoricoTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transacao;

    private TestCatalogo catalogo;
    private UUID usuarioA;
    private UUID usuarioB;
    private int p1;
    private int p2;
    private int p3;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@limpeza.test'");
        TestLogins.usuario(mvc, "a@limpeza.test");
        TestLogins.usuario(mvc, "b@limpeza.test");
        usuarioA = catalogo.usuarioId("a@limpeza.test");
        usuarioB = catalogo.usuarioId("b@limpeza.test");
        int marca = catalogo.marca("M", null);
        p1 = catalogo.produto("P1", marca, "outro", null, null);
        p2 = catalogo.produto("P2", marca, "outro", null, null);
        p3 = catalogo.produto("P3", marca, "outro", null, null);
        catalogo.historicoMatch(usuarioA, p1, p2, "compativel", null, null, null);
        catalogo.historicoMatch(usuarioA, p2, p3, "compativel", null, null, null);
        catalogo.historicoMatch(usuarioA, p1, p3, "compativel", null, null, null);
        catalogo.historicoMatch(usuarioB, p1, p2, "compativel", null, null, null);
    }

    private int linhas(UUID usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM historico_match WHERE id_usuario = ?", Integer.class, usuario);
    }

    @Test
    void apagaSoAsMisturasDoUsuarioComOProdutoRemovido() {
        transacao.executeWithoutResult(status -> events.publishEvent(new ProdutoRemovidoDaEstanteEvent(usuarioA, p1)));

        assertThat(linhas(usuarioA)).isEqualTo(1); // sobra p2+p3
        assertThat(linhas(usuarioB)).isEqualTo(1); // outro usuário intacto
    }

    @Test
    void removerProdutoSemMisturasNaoFalha() {
        int marca = catalogo.marca("M2", null);
        int solto = catalogo.produto("Solto", marca, "outro", null, null);

        transacao.executeWithoutResult(status -> events.publishEvent(new ProdutoRemovidoDaEstanteEvent(usuarioA, solto)));

        assertThat(linhas(usuarioA)).isEqualTo(3);
    }

    @Test
    void aLimpezaRodaNaTransacaoDoPublicador() {
        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
                    events.publishEvent(new ProdutoRemovidoDaEstanteEvent(usuarioA, p1));
                    throw new IllegalStateException("falha depois da remoção");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(linhas(usuarioA)).isEqualTo(3); // rollback desfez a limpeza
    }
}
