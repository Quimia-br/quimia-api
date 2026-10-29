package com.api.quimia.domain.storage;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class EstanteFavoritoWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenA;
    private String tokenB;
    private int alvejante;
    private int detergente;
    private int sabao;
    private int foraDaEstante;

    @BeforeEach
    void seed() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@favorito.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@favorito.test'");
        tokenA = TestLogins.usuario(mvc, "a@favorito.test");
        tokenB = TestLogins.usuario(mvc, "b@favorito.test");
        UUID usuarioA = catalogo.usuarioId("a@favorito.test");
        UUID usuarioB = catalogo.usuarioId("b@favorito.test");
        int marca = catalogo.marca("Marca", null);
        alvejante = catalogo.produto("Alvejante", marca, "alvejante", null, null);
        detergente = catalogo.produto("Detergente", marca, "limpeza_geral", null, null);
        sabao = catalogo.produto("Sabão", marca, "limpeza_geral", null, null);
        foraDaEstante = catalogo.produto("Fora da estante", marca, "limpeza_geral", null, null);
        for (int produto : new int[] {alvejante, detergente, sabao}) {
            catalogo.guardar(usuarioA, produto);
        }
        catalogo.guardar(usuarioB, alvejante);
    }

    private ResultActions favoritar(String token, Object produto) throws Exception {
        return mvc.perform(put("/api/v1/estante/produtos/" + produto + "/favorito").header("Authorization", "Bearer " + token));
    }

    private ResultActions desfavoritar(String token, Object produto) throws Exception {
        return mvc.perform(delete("/api/v1/estante/produtos/" + produto + "/favorito")
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions listar(String token, String... parametros) throws Exception {
        var pedido = get("/api/v1/estante/produtos").header("Authorization", "Bearer " + token);
        for (int i = 0; i < parametros.length; i += 2) {
            pedido.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(pedido);
    }

    @Test
    void produtoGuardadoComecaSemFavoritoECadaItemDizSeEhFavorito() throws Exception {
        favoritar(tokenA, sabao).andExpect(status().isNoContent());

        listar(tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Alvejante", "Detergente", "Sabão")))
                .andExpect(jsonPath("$.itens[*].favorito").value(contains(false, false, true)));
        listar(tokenA, "ordem", "recentes").andExpect(jsonPath("$.itens[?(@.nome == 'Sabão')].favorito").value(contains(true)));
    }

    @Test
    void ordemFavoritosTrazSoOsFavoritosPorNomeComBuscaEPaginacao() throws Exception {
        favoritar(tokenA, sabao);
        favoritar(tokenA, alvejante);

        listar(tokenA, "ordem", "favoritos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Alvejante", "Sabão")))
                .andExpect(jsonPath("$.itens[*].favorito").value(contains(true, true)))
                .andExpect(jsonPath("$.totalItens").value(2));
        listar(tokenA, "ordem", "favoritos", "q", "SABAO")
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Sabão")));
        listar(tokenA, "ordem", "favoritos", "page", "1", "size", "1")
                .andExpect(jsonPath("$.itens[*].nome").value(contains("Sabão")))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        listar(tokenB, "ordem", "favoritos").andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void favoritarEDesfavoritarSaoIdempotentes() throws Exception {
        favoritar(tokenA, detergente).andExpect(status().isNoContent());
        favoritar(tokenA, detergente).andExpect(status().isNoContent());
        listar(tokenA, "ordem", "favoritos").andExpect(jsonPath("$.totalItens").value(1));

        desfavoritar(tokenA, detergente).andExpect(status().isNoContent());
        desfavoritar(tokenA, detergente).andExpect(status().isNoContent());
        listar(tokenA, "ordem", "favoritos").andExpect(jsonPath("$.totalItens").value(0));
        listar(tokenA).andExpect(jsonPath("$.totalItens").value(3));
    }

    @Test
    void favoritoDeUmUsuarioNaoAfetaOutro() throws Exception {
        favoritar(tokenB, alvejante).andExpect(status().isNoContent());

        listar(tokenA, "ordem", "favoritos").andExpect(jsonPath("$.totalItens").value(0));
        listar(tokenB, "ordem", "favoritos").andExpect(jsonPath("$.itens[*].nome").value(contains("Alvejante")));
    }

    @Test
    void produtoForaDaEstanteDevolve404() throws Exception {
        favoritar(tokenA, foraDaEstante)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_esta_na_estante"));
        desfavoritar(tokenB, sabao)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_esta_na_estante"));
        favoritar(tokenA, "abc").andExpect(status().isBadRequest());
    }

    @Test
    void tirarDaEstanteEGuardarDeNovoVoltaSemFavorito() throws Exception {
        favoritar(tokenA, sabao);
        mvc.perform(delete("/api/v1/estante/produtos/" + sabao).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/estante/produtos")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoIds\":[" + sabao + "]}"))
                .andExpect(status().isOk());

        listar(tokenA, "ordem", "favoritos").andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void semTokenEComTokenDeEmpresa() throws Exception {
        mvc.perform(put("/api/v1/estante/produtos/" + sabao + "/favorito")).andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@favorito.test", "30000001000105");
        favoritar(empresa, sabao).andExpect(status().isForbidden());
        desfavoritar(empresa, sabao).andExpect(status().isForbidden());
    }
}
