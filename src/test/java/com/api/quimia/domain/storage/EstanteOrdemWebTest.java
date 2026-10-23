package com.api.quimia.domain.storage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
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
class EstanteOrdemWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void seed() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@ordem.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@ordem.test'");
        token = TestLogins.usuario(mvc, "u@ordem.test");
        var usuario = catalogo.usuarioId("u@ordem.test");
        int marca = catalogo.marca("Marca", null);
        int p = catalogo.produto("Produto", marca, "limpeza_geral", null, null);
        catalogo.guardar(usuario, p);
        int fds = catalogo.fds(p, "1", LocalDate.of(2026, 1, 1), true, null);
        int bicarbonato = catalogo.substancia("Bicarbonato de sódio", "144-55-8", "Sal alcalino suave");
        int agua = catalogo.substancia("Agua", "7732-18-5", null);
        catalogo.composto(fds, bicarbonato, "1.00", "2.00", "%");
        catalogo.composto(fds, agua, "1.00", "2.00", "%");
    }

    @Test
    void ordemAlfabeticaEhAceitaEOPadraoContinua() throws Exception {
        mvc.perform(get("/api/v1/estante/produtos").param("ordem", "alfabetica").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].nome").value("Produto"));
        mvc.perform(get("/api/v1/estante/produtos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void ordemFuturaVaziaOuDesconhecidaDevolve400() throws Exception {
        for (String ordem : new String[] {"recentes", "antigos", "favoritos", "", "x"}) {
            mvc.perform(get("/api/v1/estante/produtos").param("ordem", ordem).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("ordem_invalida"));
        }
    }

    @Test
    void substanciasTrazemDescricaoENuloQuandoNaoHa() throws Exception {
        mvc.perform(get("/api/v1/estante/substancias").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].nome").value("Agua"))
                .andExpect(jsonPath("$.itens[0].descricao").doesNotExist())
                .andExpect(jsonPath("$.itens[1].nome").value("Bicarbonato de sódio"))
                .andExpect(jsonPath("$.itens[1].descricao").value("Sal alcalino suave"));
    }

    @Test
    void substanciasFiltramPorTermoSemAcentoENoDiferenciamCaixa() throws Exception {
        mvc.perform(get("/api/v1/estante/substancias").param("q", "SODIO").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Bicarbonato de sódio"));
    }

    @Test
    void substanciasTratamPorcentoComoTexto() throws Exception {
        mvc.perform(get("/api/v1/estante/substancias").param("q", "%").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));
    }

    @Test
    void substanciasValidamTermoLongoEOrdem() throws Exception {
        mvc.perform(get("/api/v1/estante/substancias").param("q", "a".repeat(101)).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("termo_muito_longo"));
        mvc.perform(get("/api/v1/estante/substancias").param("ordem", "recentes").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("ordem_invalida"));
    }

    @Test
    void semTokenEComTokenDeEmpresa() throws Exception {
        mvc.perform(get("/api/v1/estante/substancias")).andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@ordem.test", "30000001000105");
        mvc.perform(get("/api/v1/estante/substancias").header("Authorization", "Bearer " + empresa))
                .andExpect(status().isForbidden());
    }
}
