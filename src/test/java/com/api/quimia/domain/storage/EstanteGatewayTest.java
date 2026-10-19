package com.api.quimia.domain.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

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
}
