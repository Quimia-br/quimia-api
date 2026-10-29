package com.api.quimia.domain.storage;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
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
    private TestCatalogo catalogo;
    private UUID usuario;
    private int marca;
    private int p;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@ordem.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@ordem.test'");
        token = TestLogins.usuario(mvc, "u@ordem.test");
        usuario = catalogo.usuarioId("u@ordem.test");
        marca = catalogo.marca("Marca", null);
        p = catalogo.produto("Produto", marca, "limpeza_geral", null, null);
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
    void recentesEAntigosOrdenamPelaDataEmQueFoiGuardado() throws Exception {
        int alfa = catalogo.produto("Alfa", marca, "limpeza_geral", null, null);
        int beta = catalogo.produto("Beta", marca, "limpeza_geral", null, null);
        catalogo.guardar(usuario, alfa);
        catalogo.guardar(usuario, beta);
        guardadoEm(p, "2026-01-01 10:00:00");
        guardadoEm(alfa, "2026-03-01 10:00:00");
        guardadoEm(beta, "2026-02-01 10:00:00");

        mvc.perform(get("/api/v1/estante/produtos").param("ordem", "recentes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Alfa", "Beta", "Produto")))
                .andExpect(jsonPath("$.totalItens").value(3));
        mvc.perform(get("/api/v1/estante/produtos").param("ordem", "antigos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Produto", "Beta", "Alfa")));
    }

    @Test
    void ordemPorDataPaginaEFiltraPeloTermo() throws Exception {
        int alfa = catalogo.produto("Alfa", marca, "limpeza_geral", null, null);
        int beta = catalogo.produto("Beta", marca, "limpeza_geral", null, null);
        catalogo.guardar(usuario, alfa);
        catalogo.guardar(usuario, beta);
        guardadoEm(p, "2026-01-01 10:00:00");
        guardadoEm(alfa, "2026-03-01 10:00:00");
        guardadoEm(beta, "2026-02-01 10:00:00");

        mvc.perform(get("/api/v1/estante/produtos").param("ordem", "recentes").param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Produto")))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.totalItens").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(get("/api/v1/estante/produtos").param("ordem", "recentes").param("page", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0))
                .andExpect(jsonPath("$.totalItens").value(3));
        mvc.perform(get("/api/v1/estante/produtos").param("ordem", "antigos").param("q", "ALFA")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Alfa")))
                .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void ordemPorDataComEstanteVazia() throws Exception {
        String outro = TestLogins.usuario(mvc, "vazio@ordem.test");
        mvc.perform(get("/api/v1/estante/produtos").param("ordem", "recentes").header("Authorization", "Bearer " + outro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0))
                .andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void ordemVaziaOuDesconhecidaDevolve400() throws Exception {
        for (String ordem : new String[] {"", "x", "RECENTES", "favorito"}) {
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

    private void guardadoEm(int produto, String quando) {
        jdbc.update(
                "UPDATE estante_produto SET data_adicionado = ? WHERE id_usuario = ? AND id_produto = ?",
                Timestamp.valueOf(quando), usuario, produto);
    }

    @Test
    void semTokenEComTokenDeEmpresa() throws Exception {
        mvc.perform(get("/api/v1/estante/substancias")).andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@ordem.test", "30000001000105");
        mvc.perform(get("/api/v1/estante/substancias").header("Authorization", "Bearer " + empresa))
                .andExpect(status().isForbidden());
    }
}
