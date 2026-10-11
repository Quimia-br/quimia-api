package com.api.quimia.domain.partner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(PartnerDoubles.class)
class PontoWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestCatalogo catalogo;
    private String token;
    private UUID usuario;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM localizacao_usuario WHERE id_usuario IN (SELECT id FROM usuario WHERE email LIKE '%@ponto.test')");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@ponto.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@ponto.test'");
        token = TestLogins.usuario(mvc, "u@ponto.test");
        usuario = catalogo.usuarioId("u@ponto.test");
    }

    private ResultActions buscar(String... parametros) throws Exception {
        MockHttpServletRequestBuilder pedido = get("/api/v1/pontos").header("Authorization", "Bearer " + token);
        for (int i = 0; i < parametros.length; i += 2) {
            pedido.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(pedido);
    }

    private void cadastrarLocalizacao(String cep, String estado, String bairro) {
        jdbc.update("INSERT INTO localizacao_usuario (id_usuario, cep, estado, bairro) VALUES (?, ?, ?, ?)", usuario, cep, estado, bairro);
    }

    @Test
    void filtraPorTipoESoDevolvePontosAtivos() throws Exception {
        catalogo.pontoParceiro("Descarte A", "descarte", "01001-000", "SP", "Centro", "TRUE");
        catalogo.pontoParceiro("Descarte nulo", "descarte", "01002-000", "SP", "Centro", "NULL");
        catalogo.pontoParceiro("Descarte inativo", "descarte", "01003-000", "SP", "Centro", "FALSE");
        catalogo.pontoParceiro("Loja", "compra", "01004-000", "SP", "Centro", "TRUE");

        buscar("tipo", "descarte")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(2))
                .andExpect(jsonPath("$.itens[0].nome").value("Descarte A"))
                .andExpect(jsonPath("$.itens[1].nome").value("Descarte nulo"))
                .andExpect(jsonPath("$.itens[0].tipo").value("descarte"));
        buscar("tipo", "compra")
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Loja"));
    }

    @Test
    void tipoAusenteOuInvalidoDa400() throws Exception {
        buscar().andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("tipo_invalido"));
        buscar("tipo", "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("tipo_invalido"));
        buscar("tipo", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("tipo_invalido"));
    }

    @Test
    void estadoEhFiltroNormalizadoEInvalidoDa422() throws Exception {
        catalogo.pontoParceiro("SP", "descarte", null, "SP", "Centro", "TRUE");
        catalogo.pontoParceiro("RJ", "descarte", null, "RJ", "Centro", "TRUE");

        buscar("tipo", "descarte", "estado", "sp")
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("SP"));
        buscar("tipo", "descarte", "estado", "SPX")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("estado_invalido"));
        buscar("tipo", "descarte", "estado", "1A")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void bairroEFiltroSemAcentoNemCaixaEPorcentoEhTexto() throws Exception {
        catalogo.pontoParceiro("Centro", "descarte", null, "SP", "Centro", "TRUE");
        catalogo.pontoParceiro("Vila", "descarte", null, "SP", "Vila Olímpia", "TRUE");

        buscar("tipo", "descarte", "bairro", "centro").andExpect(jsonPath("$.totalItens").value(1));
        buscar("tipo", "descarte", "bairro", "VILA OLIMPIA").andExpect(jsonPath("$.itens[0].nome").value("Vila"));
        buscar("tipo", "descarte", "bairro", "%").andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void semCriterioDeLocalListaTudoOrdenadoPorEstadoNomeEId() throws Exception {
        catalogo.pontoParceiro("B", "descarte", null, "SP", "X", "TRUE");
        catalogo.pontoParceiro("A", "descarte", null, "SP", "X", "TRUE");
        catalogo.pontoParceiro("Z", "descarte", null, "AC", "X", "TRUE");
        catalogo.pontoParceiro("Sem estado", "descarte", null, null, "X", "TRUE");

        buscar("tipo", "descarte")
                .andExpect(jsonPath("$.itens[0].nome").value("Z"))
                .andExpect(jsonPath("$.itens[1].nome").value("A"))
                .andExpect(jsonPath("$.itens[2].nome").value("B"))
                .andExpect(jsonPath("$.itens[3].nome").value("Sem estado"));
    }

    @Test
    void paginacaoValidaELimites() throws Exception {
        for (int i = 0; i < 3; i++) {
            catalogo.pontoParceiro("P" + i, "compra", null, "SP", "X", "TRUE");
        }
        buscar("tipo", "compra", "size", "2")
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.totalItens").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        buscar("tipo", "compra", "size", "2", "page", "1")
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("P2"));
        buscar("tipo", "compra", "page", "99")
                .andExpect(jsonPath("$.itens.length()").value(0));
        for (String[] par : new String[][] {{"size", "51"}, {"size", "0"}, {"page", "-1"}, {"page", "2147483647"}}) {
            buscar("tipo", "compra", par[0], par[1])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("paginacao_invalida"));
        }
        buscar("tipo", "compra", "page", "abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("parametro_invalido"));
    }

    @Test
    void respostaNaoTemCoordenadasNemDadosDeEmpresa() throws Exception {
        catalogo.pontoParceiro("Ponto", "descarte", "01001-000", "SP", "Centro", "TRUE");

        buscar("tipo", "descarte")
                .andExpect(jsonPath("$.itens[0].cep").value("01001-000"))
                .andExpect(jsonPath("$.itens[0].latitude").doesNotExist())
                .andExpect(jsonPath("$.itens[0].distancia").doesNotExist())
                .andExpect(jsonPath("$.itens[0].empresaId").doesNotExist());
    }

    @Test
    void autenticacao() throws Exception {
        mvc.perform(get("/api/v1/pontos").param("tipo", "compra")).andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@ponto.test", "30000004000130");
        mvc.perform(get("/api/v1/pontos").param("tipo", "compra").header("Authorization", "Bearer " + empresa))
                .andExpect(status().isForbidden());
    }

    @Test
    void cepIgualVemAntesDoMesmoPrefixoQueVemAntesDoResto() throws Exception {
        catalogo.pontoParceiro("Longe", "descarte", "99999-000", "SP", "Centro", "TRUE");
        catalogo.pontoParceiro("Prefixo", "descarte", "01001-999", "SP", "Centro", "TRUE");
        catalogo.pontoParceiro("Exato", "descarte", "01001-000", "SP", "Centro", "TRUE");

        buscar("tipo", "descarte", "cep", "01001000")
                .andExpect(jsonPath("$.itens[0].nome").value("Exato"))
                .andExpect(jsonPath("$.itens[1].nome").value("Prefixo"))
                .andExpect(jsonPath("$.itens[2].nome").value("Longe"));
        buscar("tipo", "descarte", "cep", "01001-000")
                .andExpect(jsonPath("$.itens[0].nome").value("Exato"));
    }

    @Test
    void cepNaoFiltraSoOrdena() throws Exception {
        catalogo.pontoParceiro("Outro CEP", "descarte", "99999-000", "SP", "Centro", "TRUE");

        buscar("tipo", "descarte", "cep", "01001000").andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void cepForaDoFormatoDa422() throws Exception {
        for (String cep : new String[] {"123", "abcdefgh", "0100100", "010010000"}) {
            buscar("tipo", "descarte", "cep", cep)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("cep_invalido"));
        }
    }

    @Test
    void pontoSemCepFicaNoFimDoRanking() throws Exception {
        catalogo.pontoParceiro("Sem CEP", "descarte", null, "SP", "Centro", "TRUE");
        catalogo.pontoParceiro("Exato", "descarte", "01001-000", "SP", "Centro", "TRUE");

        buscar("tipo", "descarte", "cep", "01001000")
                .andExpect(jsonPath("$.itens[0].nome").value("Exato"))
                .andExpect(jsonPath("$.itens[1].nome").value("Sem CEP"));
    }

    @Test
    void usaOEnderecoCadastradoQuandoPedido() throws Exception {
        cadastrarLocalizacao("01001-000", "SP", "Centro");
        catalogo.pontoParceiro("Do Centro", "descarte", "01001-000", "SP", "Centro", "TRUE");
        catalogo.pontoParceiro("Outro bairro", "descarte", "02002-000", "SP", "Mooca", "TRUE");
        catalogo.pontoParceiro("Outro estado", "descarte", "20000-000", "RJ", "Centro", "TRUE");

        buscar("tipo", "descarte", "usarLocalizacaoCadastrada", "true")
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Do Centro"));
        buscar("tipo", "descarte")
                .andExpect(jsonPath("$.totalItens").value(3));
    }

    @Test
    void parametroExplicitoVenceOCadastrado() throws Exception {
        cadastrarLocalizacao("01001-000", "SP", "Centro");
        catalogo.pontoParceiro("SP", "descarte", null, "SP", "Centro", "TRUE");
        catalogo.pontoParceiro("RJ", "descarte", null, "RJ", "Centro", "TRUE");

        buscar("tipo", "descarte", "usarLocalizacaoCadastrada", "true", "estado", "RJ")
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("RJ"));
    }

    @Test
    void semEnderecoCadastradoDa422() throws Exception {
        buscar("tipo", "descarte", "usarLocalizacaoCadastrada", "true")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("localizacao_necessaria"));
    }

    @Test
    void campoNuloNoEnderecoCadastradoEIgnorado() throws Exception {
        cadastrarLocalizacao(null, "SP", null);
        catalogo.pontoParceiro("SP", "descarte", null, "SP", "Centro", "TRUE");
        catalogo.pontoParceiro("RJ", "descarte", null, "RJ", "Centro", "TRUE");

        buscar("tipo", "descarte", "usarLocalizacaoCadastrada", "true")
                .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void booleanInvalidoDa400() throws Exception {
        buscar("tipo", "descarte", "usarLocalizacaoCadastrada", "talvez")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("parametro_invalido"));
    }
}
