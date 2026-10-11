package com.api.quimia.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(RecommendationDoubles.class)
class MisturaGatewayTest {
    @Autowired
    private MisturaGateway gateway;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contaPorResultadoSemDuplicarEIncluindoVariosUsuarios() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@gw-mistura.test'");
        TestLogins.usuario(mvc, "a@gw-mistura.test");
        TestLogins.usuario(mvc, "b@gw-mistura.test");
        UUID a = catalogo.usuarioId("a@gw-mistura.test");
        UUID b = catalogo.usuarioId("b@gw-mistura.test");
        int marca = catalogo.marca("M", null);
        int p1 = catalogo.produto("P1", marca, "outro", null, null);
        int p2 = catalogo.produto("P2", marca, "outro", null, null);
        int p3 = catalogo.produto("P3", marca, "outro", null, null);
        int fora = catalogo.produto("Fora", marca, "outro", null, null);
        catalogo.historicoMatch(a, p1, p2, "compativel", null, null, null); // p2 na lista
        catalogo.historicoMatch(b, p1, p3, "incompativel", "alta", "Risco", null); // p3 na lista
        catalogo.historicoMatch(a, p2, p3, "nao_avaliado", null, "Sem FDS", null); // os dois na lista: conta 1
        catalogo.historicoMatch(a, p3, fora, "compativel", null, null, null); // p3 na lista
        catalogo.historicoMatch(a, p1, fora, "compativel", null, null, null); // nenhum na lista

        ResumoMisturasView resumo = gateway.resumoDosProdutos(List.of(p2, p3));

        assertThat(resumo.total()).isEqualTo(4);
        assertThat(resumo.compativeis()).isEqualTo(2);
        assertThat(resumo.incompativeis()).isEqualTo(1);
        assertThat(resumo.naoAvaliadas()).isEqualTo(1);
        assertThat(gateway.resumoDosProdutos(List.of())).isEqualTo(new ResumoMisturasView(0, 0, 0, 0));
    }
}
