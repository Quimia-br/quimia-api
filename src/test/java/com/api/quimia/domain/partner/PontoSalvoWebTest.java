package com.api.quimia.domain.partner;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.sql.Timestamp;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class PontoSalvoWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestCatalogo catalogo;
    private String tokenA;
    private String tokenB;
    private UUID usuarioA;
    private int ecoponto;
    private int cooperativa;
    private int loja;
    private int inativo;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@salvo.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@salvo.test'");
        tokenA = TestLogins.usuario(mvc, "a@salvo.test");
        tokenB = TestLogins.usuario(mvc, "b@salvo.test");
        usuarioA = catalogo.usuarioId("a@salvo.test");
        ecoponto = catalogo.pontoParceiro("Ecoponto", "descarte", "01001-000", "SP", "Centro", "TRUE");
        jdbc.update("UPDATE ponto_parceiro SET latitude = -23.550520, longitude = -46.633308 WHERE id = ?", ecoponto);
        cooperativa = catalogo.pontoParceiro("Cooperativa", "descarte", "01002-000", "SP", "Centro", "NULL");
        loja = catalogo.pontoParceiro("Loja", "compra", "01003-000", "SP", "Centro", "TRUE");
        inativo = catalogo.pontoParceiro("Fechado", "descarte", "01004-000", "SP", "Centro", "FALSE");
    }

    private ResultActions salvar(String token, Object pontoId) throws Exception {
        return mvc.perform(put("/api/v1/pontos/salvos/" + pontoId).header("Authorization", "Bearer " + token));
    }

    private ResultActions remover(String token, Object pontoId) throws Exception {
        return mvc.perform(delete("/api/v1/pontos/salvos/" + pontoId).header("Authorization", "Bearer " + token));
    }

    private ResultActions buscar(String token, String caminho, String... parametros) throws Exception {
        var pedido = get("/api/v1/pontos/salvos" + caminho).header("Authorization", "Bearer " + token);
        for (int i = 0; i < parametros.length; i += 2) {
            pedido.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(pedido);
    }

    private void salvoEm(int pontoId, String quando) {
        jdbc.update(
                "UPDATE ponto_salvo SET criado_em = ? WHERE id_usuario = ? AND id_ponto = ?",
                Timestamp.valueOf(quando), usuarioA, pontoId);
    }

    @Test
    void salvarDevolveOPontoComCoordenadasEDataEERepetirNaoDuplica() throws Exception {
        salvar(tokenA, ecoponto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ecoponto))
                .andExpect(jsonPath("$.nome").value("Ecoponto"))
                .andExpect(jsonPath("$.tipo").value("descarte"))
                .andExpect(jsonPath("$.latitude").value(-23.550520))
                .andExpect(jsonPath("$.longitude").value(-46.633308))
                .andExpect(jsonPath("$.salvoEm").isNotEmpty());
        salvoEm(ecoponto, "2026-01-01 10:00:00");

        salvar(tokenA, ecoponto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salvoEm").value("2026-01-01T10:00:00"));
        buscar(tokenA, "").andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void salvarPontoInexistenteOuDesativadoDevolve404() throws Exception {
        salvar(tokenA, inativo)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("ponto_nao_encontrado"));
        salvar(tokenA, 999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("ponto_nao_encontrado"));
        salvar(tokenA, "abc").andExpect(status().isBadRequest());
    }

    @Test
    void listaDoMaisRecenteAoMaisAntigoFiltraPorTipoEPagina() throws Exception {
        salvar(tokenA, ecoponto);
        salvar(tokenA, cooperativa);
        salvar(tokenA, loja);
        salvoEm(ecoponto, "2026-01-01 10:00:00");
        salvoEm(cooperativa, "2026-03-01 10:00:00");
        salvoEm(loja, "2026-02-01 10:00:00");

        buscar(tokenA, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Cooperativa", "Loja", "Ecoponto")))
                .andExpect(jsonPath("$.totalItens").value(3));
        buscar(tokenA, "", "tipo", "descarte")
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Cooperativa", "Ecoponto")));
        buscar(tokenA, "", "page", "1", "size", "2")
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Ecoponto")))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void listaNaoMostraPontoDesativadoDepoisDeSalvoNemOsDeOutroUsuario() throws Exception {
        salvar(tokenA, ecoponto);
        salvar(tokenB, loja);
        jdbc.update("UPDATE ponto_parceiro SET ativo = FALSE WHERE id = ?", ecoponto);

        buscar(tokenA, "").andExpect(jsonPath("$.totalItens").value(0));
        buscar(tokenB, "").andExpect(jsonPath("$.itens[*].nome").value(contains("Loja")));
    }

    @Test
    void ultimoDescarteEhOMaisRecenteEVoltaAoAnteriorQuandoRemovido() throws Exception {
        salvar(tokenA, ecoponto);
        salvar(tokenA, cooperativa);
        salvar(tokenA, loja);
        salvoEm(ecoponto, "2026-01-01 10:00:00");
        salvoEm(cooperativa, "2026-02-01 10:00:00");
        salvoEm(loja, "2026-03-01 10:00:00");

        buscar(tokenA, "/ultimo", "tipo", "descarte")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cooperativa"));
        buscar(tokenA, "/ultimo").andExpect(jsonPath("$.nome").value("Loja"));

        remover(tokenA, cooperativa).andExpect(status().isNoContent());
        buscar(tokenA, "/ultimo", "tipo", "descarte").andExpect(jsonPath("$.nome").value("Ecoponto"));
    }

    @Test
    void ultimoSemNenhumSalvoDevolve404() throws Exception {
        salvar(tokenA, loja);

        buscar(tokenA, "/ultimo", "tipo", "descarte")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("ponto_salvo_nao_encontrado"));
        buscar(tokenB, "/ultimo")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("ponto_salvo_nao_encontrado"));
    }

    @Test
    void removerEhIdempotenteESoAfetaOProprioUsuario() throws Exception {
        salvar(tokenA, ecoponto);
        salvar(tokenB, ecoponto);

        remover(tokenA, ecoponto).andExpect(status().isNoContent());
        remover(tokenA, ecoponto).andExpect(status().isNoContent());
        remover(tokenA, 999999).andExpect(status().isNoContent());

        buscar(tokenA, "").andExpect(jsonPath("$.totalItens").value(0));
        buscar(tokenB, "").andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void validaTipoEPaginacao() throws Exception {
        buscar(tokenA, "", "tipo", "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("tipo_invalido"));
        buscar(tokenA, "/ultimo", "tipo", "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("tipo_invalido"));
        buscar(tokenA, "", "size", "51")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("paginacao_invalida"));
        buscar(tokenA, "", "page", "-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("paginacao_invalida"));
    }

    @Test
    void semTokenEComTokenDeEmpresa() throws Exception {
        mvc.perform(get("/api/v1/pontos/salvos")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/pontos/salvos/" + ecoponto)).andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@salvo.test", "30000001000105");
        mvc.perform(get("/api/v1/pontos/salvos/ultimo").header("Authorization", "Bearer " + empresa))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/pontos/salvos/" + ecoponto).header("Authorization", "Bearer " + empresa))
                .andExpect(status().isForbidden());
    }
}
