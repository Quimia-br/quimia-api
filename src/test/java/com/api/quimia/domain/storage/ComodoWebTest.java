package com.api.quimia.domain.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
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
class ComodoWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestCatalogo catalogo;
    private String tokenA;
    private String tokenB;
    private UUID usuarioA;
    private UUID usuarioB;
    private int p1;
    private int p2;
    private int p3;
    private int doOutro;
    private int marca;
    private int agua;

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@comodo.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@comodo.catalogo' OR email LIKE '%@comodo.test'");
        tokenA = TestLogins.usuario(mvc, "a@comodo.test");
        tokenB = TestLogins.usuario(mvc, "b@comodo.test");
        usuarioA = catalogo.usuarioId("a@comodo.test");
        usuarioB = catalogo.usuarioId("b@comodo.test");
        marca = catalogo.marca("Marca", null);
        int hipoclorito = catalogo.substancia("Hipoclorito de sódio", "7681-52-9", null);
        int amonia = catalogo.substancia("Amônia", "7664-41-7", null);
        agua = catalogo.substancia("Água", "7732-18-5", null);
        catalogo.regra(hipoclorito, null, amonia, null, "critica", "Libera gás tóxico", true);
        p1 = produtoCom("Alvejante", hipoclorito);
        p2 = produtoCom("Limpa vidros", amonia);
        p3 = produtoCom("Desengordurante", agua);
        doOutro = produtoCom("Do outro usuário", agua);
        for (int produto : new int[] {p1, p2, p3}) {
            catalogo.guardar(usuarioA, produto);
        }
        catalogo.guardar(usuarioB, doOutro);
    }

    /** Produto com FDS ativa e os compostos informados. */
    private int produtoCom(String nome, int... substancias) {
        int produto = catalogo.produto(nome, marca, "limpeza_geral", null, null);
        int fds = catalogo.fds(produto, "1", LocalDate.of(2026, 1, 1), true, null);
        for (int substancia : substancias) {
            catalogo.composto(fds, substancia, "1.00", "5.00", "%");
        }
        return produto;
    }

    private ResultActions criar(String token, String nome, String icone, int... produtos) throws Exception {
        String ids = Arrays.stream(produtos).mapToObj(String::valueOf).collect(Collectors.joining(","));
        return mvc.perform(post("/api/v1/comodos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"icone\":\"" + icone + "\",\"produtoIds\":[" + ids + "]}"));
    }

    private int criarEObterId(String token, String nome, int... produtos) throws Exception {
        String corpo = criar(token, nome, "icone", produtos).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.id");
    }

    // --- POST /comodos ---

    @Test
    void criaComodoComDoisProdutosEDevolveAvaliacao() throws Exception {
        criar(tokenA, "Banheiro", "banheira", p1, p2)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/comodos/\\d+")))
                .andExpect(jsonPath("$.nome").value("Banheiro"))
                .andExpect(jsonPath("$.icone").value("banheira"))
                .andExpect(jsonPath("$.avaliacao.status").value("critico"))
                .andExpect(jsonPath("$.avaliacao.paresIncompativeis").value(1))
                .andExpect(jsonPath("$.avaliacao.incompatibilidades[0].severidade").value("critica"))
                .andExpect(jsonPath("$.avaliacao.incompatibilidades[0].descricaoRisco").value("Libera gás tóxico"))
                .andExpect(jsonPath("$.produtos.length()").value(2))
                .andExpect(jsonPath("$.produtos[0].nome").value("Alvejante"));
        Integer noComodo = jdbc.queryForObject(
                "SELECT count(*) FROM estante_produto WHERE id_usuario = ? AND id_comodo IS NOT NULL", Integer.class, usuarioA);
        assertThat(noComodo).isEqualTo(2);
    }

    @Test
    void umProdutoSoListaVaziaOuIdsRepetidosNaoBastam() throws Exception {
        criar(tokenA, "Banheiro", "banheira", p1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("comodo_produtos_insuficientes"));
        criar(tokenA, "Banheiro", "banheira")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("comodo_produtos_insuficientes"));
        criar(tokenA, "Banheiro", "banheira", p1, p1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("comodo_produtos_insuficientes"));
    }

    @Test
    void maisDeVinteProdutosEhRecusado() throws Exception {
        int[] muitos = new int[21];
        int marca = catalogo.marca("Outra", null);
        for (int i = 0; i < muitos.length; i++) {
            muitos[i] = catalogo.produto("Produto " + i, marca, "outro", null, null);
            catalogo.guardar(usuarioA, muitos[i]);
        }
        criar(tokenA, "Despensa", "caixa", muitos)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("comodo_produtos_demais"));
    }

    @Test
    void produtoForaDaEstanteOuDeOutroUsuarioEhRecusado() throws Exception {
        criar(tokenA, "Banheiro", "banheira", p1, doOutro)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("produto_fora_da_estante"));
        criar(tokenA, "Banheiro", "banheira", p1, 999999)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("produto_fora_da_estante"));
    }

    @Test
    void produtoJaEmOutroComodoDaConflitoENadaMuda() throws Exception {
        criar(tokenA, "Banheiro", "banheira", p1, p2).andExpect(status().isCreated());

        criar(tokenA, "Cozinha", "fogao", p2, p3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("produto_em_outro_comodo"));

        Integer p3NoComodo = jdbc.queryForObject(
                "SELECT count(*) FROM estante_produto WHERE id_usuario = ? AND id_produto = ? AND id_comodo IS NOT NULL",
                Integer.class, usuarioA, p3);
        Integer comodos = jdbc.queryForObject("SELECT count(*) FROM comodo WHERE id_usuario = ?", Integer.class, usuarioA);
        assertThat(p3NoComodo).isZero();
        assertThat(comodos).isEqualTo(1);
    }

    @Test
    void nomeRepetidoSemDiferenciarCaixaEAcentoDaConflito() throws Exception {
        criar(tokenA, "Sala de estar", "sofa", p1, p2).andExpect(status().isCreated());
        int marca = catalogo.marca("Terceira", null);
        int p4 = catalogo.produto("P4", marca, "outro", null, null);
        int p5 = catalogo.produto("P5", marca, "outro", null, null);
        catalogo.guardar(usuarioA, p4);
        catalogo.guardar(usuarioA, p5);

        criar(tokenA, "  SÁLA  de   ESTAR ", "sofa", p4, p5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("comodo_ja_existe"));
    }

    @Test
    void outroUsuarioPodeUsarOMesmoNome() throws Exception {
        criar(tokenA, "Banheiro", "banheira", p1, p2).andExpect(status().isCreated());
        int marca = catalogo.marca("Outra", null);
        int x = catalogo.produto("X", marca, "outro", null, null);
        int y = catalogo.produto("Y", marca, "outro", null, null);
        catalogo.guardar(usuarioB, x);
        catalogo.guardar(usuarioB, y);

        criar(tokenB, "Banheiro", "banheira", x, y).andExpect(status().isCreated());
    }

    @Test
    void validacoesDeCorpo() throws Exception {
        criar(tokenA, "", "banheira", p1, p2).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("validation"));
        criar(tokenA, "Banheiro", "Banheira!", p1, p2).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/comodos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("corpo_invalido"));
        mvc.perform(post("/api/v1/comodos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/v1/comodos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"icone\":\"a\",\"produtoIds\":[1.5,2]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("corpo_invalido"));
    }

    @Test
    void autenticacao() throws Exception {
        mvc.perform(post("/api/v1/comodos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@comodo.test", "30000002000141");
        criar(empresa, "Banheiro", "banheira", p1, p2).andExpect(status().isForbidden());
    }

    // --- GET /comodos e /comodos/{id} ---

    @Test
    void listaSoOsComodosDoUsuarioOrdenadosPorNomeSemAcento() throws Exception {
        criarEObterId(tokenA, "Quarto", p1, p2);
        int x = produtoCom("X", agua);
        int y = produtoCom("Y", agua);
        catalogo.guardar(usuarioA, x);
        catalogo.guardar(usuarioA, y);
        criarEObterId(tokenA, "Área de serviço", x, y);

        mvc.perform(get("/api/v1/comodos").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(2))
                .andExpect(jsonPath("$.itens[0].nome").value("Área de serviço"))
                .andExpect(jsonPath("$.itens[0].totalProdutos").value(2))
                .andExpect(jsonPath("$.itens[0].status").value("seguro"))
                .andExpect(jsonPath("$.itens[1].nome").value("Quarto"))
                .andExpect(jsonPath("$.itens[1].status").value("critico"));
        mvc.perform(get("/api/v1/comodos").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));
    }

    @Test
    void listaFiltraPorTermoEValidaPaginacao() throws Exception {
        criarEObterId(tokenA, "Banheiro", p1, p2);
        mvc.perform(get("/api/v1/comodos").param("q", "BANH").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.itens.length()").value(1));
        mvc.perform(get("/api/v1/comodos").param("q", "cozinha").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.itens.length()").value(0));
        mvc.perform(get("/api/v1/comodos").param("size", "51").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("paginacao_invalida"));
        mvc.perform(get("/api/v1/comodos").param("page", "-1").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/comodos").param("q", "a".repeat(101)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("termo_muito_longo"));
    }

    @Test
    void detalheDevolveProdutosOrdenadosEAvaliacao() throws Exception {
        int id = criarEObterId(tokenA, "Banheiro", p2, p1);

        mvc.perform(get("/api/v1/comodos/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avaliacao.status").value("critico"))
                .andExpect(jsonPath("$.produtos[0].nome").value("Alvejante"))
                .andExpect(jsonPath("$.produtos[1].nome").value("Limpa vidros"));
    }

    @Test
    void detalheDeOutroUsuarioEInexistenteDa404IgualEIdInvalidoDa400() throws Exception {
        int id = criarEObterId(tokenA, "Banheiro", p1, p2);

        mvc.perform(get("/api/v1/comodos/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("comodo_nao_encontrado"));
        mvc.perform(get("/api/v1/comodos/999999").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("comodo_nao_encontrado"));
        mvc.perform(get("/api/v1/comodos/abc").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("parametro_invalido"));
        mvc.perform(get("/api/v1/comodos/2147483648").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("parametro_invalido"));
    }

    @Test
    void produtoOcultoNoCatalogoContinuaNoComodo() throws Exception {
        int id = criarEObterId(tokenA, "Banheiro", p1, p2);
        int empresa = catalogo.empresa("Empresa", "e@comodo.catalogo");
        catalogo.vinculo(empresa, p1, "FALSE"); // única empresa desativou: oculto no catálogo

        mvc.perform(get("/api/v1/comodos/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos.length()").value(2));
    }

    // --- POST /comodos/{id}/produtos e DELETE ---

    @Test
    void adicionaProdutoERecalcula() throws Exception {
        String corpo = criar(tokenA, "Banheiro", "icone", p1, p3)
                .andExpect(jsonPath("$.avaliacao.status").value("seguro"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(corpo, "$.id");

        mvc.perform(post("/api/v1/comodos/" + id + "/produtos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIds\":[" + p2 + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos.length()").value(3))
                .andExpect(jsonPath("$.avaliacao.status").value("critico"))
                .andExpect(jsonPath("$.avaliacao.totalProdutos").value(3))
                .andExpect(jsonPath("$.avaliacao.paresAvaliados").value(3))
                .andExpect(jsonPath("$.avaliacao.paresIncompativeis").value(1));
        mvc.perform(delete("/api/v1/comodos/" + id + "/produtos/" + p2).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/comodos/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.avaliacao.status").value("seguro"));
    }

    @Test
    void produtoSemFdsDeixaOComodoNaoAvaliadoEFdsNovaReavalia() throws Exception {
        int semFds = catalogo.produto("Sem FDS", marca, "outro", null, null);
        catalogo.guardar(usuarioA, semFds);
        int id = criarEObterId(tokenA, "Lavanderia", p3, semFds);

        mvc.perform(get("/api/v1/comodos/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.avaliacao.status").value("nao_avaliado"));
        catalogo.composto(catalogo.fds(semFds, "1", LocalDate.of(2026, 2, 1), true, null), agua, "1.00", "2.00", "%");
        mvc.perform(get("/api/v1/comodos/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.avaliacao.status").value("seguro"));
    }

    @Test
    void adicionarDeNovoNaoMudaNada() throws Exception {
        int id = criarEObterId(tokenA, "Banheiro", p1, p2);

        mvc.perform(post("/api/v1/comodos/" + id + "/produtos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIds\":[" + p1 + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos.length()").value(2));
    }

    @Test
    void adicionarProdutoDeOutroComodoOuForaDaEstanteOuDeOutroUsuarioEhRecusado() throws Exception {
        int banheiro = criarEObterId(tokenA, "Banheiro", p1, p2);
        int marca = catalogo.marca("M", null);
        int x = catalogo.produto("X", marca, "outro", null, null);
        int y = catalogo.produto("Y", marca, "outro", null, null);
        catalogo.guardar(usuarioA, x);
        catalogo.guardar(usuarioA, y);
        int cozinha = criarEObterId(tokenA, "Cozinha", x, y);

        mvc.perform(post("/api/v1/comodos/" + cozinha + "/produtos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIds\":[" + p1 + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("produto_em_outro_comodo"));
        mvc.perform(post("/api/v1/comodos/" + banheiro + "/produtos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIds\":[" + doOutro + "]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("produto_fora_da_estante"));
        mvc.perform(post("/api/v1/comodos/" + banheiro + "/produtos").header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIds\":[" + p3 + "]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("comodo_nao_encontrado"));
    }

    @Test
    void adicionarAcimaDeVinteEhRecusado() throws Exception {
        int id = criarEObterId(tokenA, "Despensa", p1, p2);
        int marca = catalogo.marca("Muitas", null);
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 19; i++) {
            int produto = catalogo.produto("Extra " + i, marca, "outro", null, null);
            catalogo.guardar(usuarioA, produto);
            ids.append(i == 0 ? "" : ",").append(produto);
        }
        mvc.perform(post("/api/v1/comodos/" + id + "/produtos").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"produtoIds\":[" + ids + "]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("comodo_produtos_demais"));
    }

    @Test
    void removeProdutoDoComodoSemTirarDaEstanteERepetirEIdempotente() throws Exception {
        int id = criarEObterId(tokenA, "Banheiro", p1, p2);

        mvc.perform(delete("/api/v1/comodos/" + id + "/produtos/" + p1).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/comodos/" + id + "/produtos/" + p1).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/comodos/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.produtos.length()").value(1));
        mvc.perform(get("/api/v1/estante/produtos").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.totalItens").value(3));
    }

    @Test
    void removerEmComodoAlheioDa404EIdInvalidoDa400() throws Exception {
        int id = criarEObterId(tokenA, "Banheiro", p1, p2);

        mvc.perform(delete("/api/v1/comodos/" + id + "/produtos/" + p1).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/comodos/" + id + "/produtos/abc").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tirarDaEstanteLimpaOVinculoComOComodo() throws Exception {
        int id = criarEObterId(tokenA, "Banheiro", p1, p2);

        mvc.perform(delete("/api/v1/estante/produtos/" + p1).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/comodos/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.produtos.length()").value(1));
    }

    // --- GET /estante/produtos/{id}/comodo ---

    @Test
    void comodoDoProdutoDevolveOResumoOu404ConformeOCaso() throws Exception {
        int id = criarEObterId(tokenA, "Banheiro", p1, p2);

        mvc.perform(get("/api/v1/estante/produtos/" + p1 + "/comodo").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nome").value("Banheiro"))
                .andExpect(jsonPath("$.totalProdutos").value(2));
        mvc.perform(get("/api/v1/estante/produtos/" + p3 + "/comodo").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_sem_comodo"));
        mvc.perform(get("/api/v1/estante/produtos/" + doOutro + "/comodo").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_esta_na_estante"));
        mvc.perform(get("/api/v1/estante/produtos/" + p1 + "/comodo").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_esta_na_estante"));
    }
}
