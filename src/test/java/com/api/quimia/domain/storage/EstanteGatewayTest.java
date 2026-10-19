package com.api.quimia.domain.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class EstanteGatewayTest {
    @Autowired
    private EstanteGateway gateway;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacoes;

    @Test
    void produtoIdsDoUsuarioSoDevolveOsDeleEContarSomaUsuarios() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@gateway.test'");
        TestLogins.usuario(mvc, "a@gateway.test");
        TestLogins.usuario(mvc, "b@gateway.test");
        UUID a = catalogo.usuarioId("a@gateway.test");
        UUID b = catalogo.usuarioId("b@gateway.test");
        int marca = catalogo.marca("M", null);
        int p1 = catalogo.produto("P1", marca, "outro", null, null);
        int p2 = catalogo.produto("P2", marca, "outro", null, null);
        catalogo.guardar(a, p1);
        catalogo.guardar(a, p2);
        catalogo.guardar(b, p1);

        assertThat(gateway.produtoIdsDoUsuario(a)).containsExactlyInAnyOrder(p1, p2);
        assertThat(gateway.produtoIdsDoUsuario(b)).containsExactly(p1);
        assertThat(gateway.produtoIdsDoUsuario(UUID.randomUUID())).isEmpty();
        assertThat(gateway.contarGuardados(List.of(p1))).isEqualTo(2);
        assertThat(gateway.contarGuardados(List.of(p1, p2))).isEqualTo(3);
        assertThat(gateway.contarGuardados(List.of())).isZero();
    }

    @Test
    void travarNaEstanteDevolveSoOsDoUsuarioEExigeTransacao() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@trava.test'");
        TestLogins.usuario(mvc, "a@trava.test");
        TestLogins.usuario(mvc, "b@trava.test");
        UUID a = catalogo.usuarioId("a@trava.test");
        UUID b = catalogo.usuarioId("b@trava.test");
        int marca = catalogo.marca("M", null);
        int p1 = catalogo.produto("P1", marca, "outro", null, null);
        int p2 = catalogo.produto("P2", marca, "outro", null, null);
        catalogo.guardar(a, p1);
        catalogo.guardar(b, p2);
        TransactionTemplate transacao = new TransactionTemplate(transacoes);

        Set<Integer> doUsuario = transacao.execute(status -> gateway.travarNaEstante(a, List.of(p1, p2, 999999)));
        Set<Integer> nenhum = transacao.execute(status -> gateway.travarNaEstante(a, List.of()));
        Set<Integer> semEstante = transacao.execute(status -> gateway.travarNaEstante(UUID.randomUUID(), List.of(p1)));

        assertThat(doUsuario).containsExactly(p1);
        assertThat(nenhum).isEmpty();
        assertThat(semEstante).isEmpty();
        assertThatThrownBy(() -> gateway.travarNaEstante(a, List.of(p1)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
