package com.api.quimia.domain.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import org.hamcrest.Matchers;
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

/**
 * `fields` na listagem do portal escolhe em quais campos o `q` é procurado (chips "Campos da busca" do web: Nome,
 * Tipo e Código de barras). A busca do app não tem `fields` e não muda.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class CamposDaBuscaWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenUsuario;
    private String tokenEmpresa;
    private int desinfetante;
    private int limpador;
    private int daMarca;

    @BeforeEach
    void seed() throws Exception {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@campos-busca.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@campos-busca.test'");
        tokenUsuario = TestLogins.usuario(mvc, "u@campos-busca.test");
        tokenEmpresa = TestLogins.empresa(mvc, "Empresa Campos", "e@campos-busca.test", "30000012000187");
        int empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@campos-busca.test'", Integer.class);
        int marca = catalogo.marca("Marca Comum", null);
        int marcaPinho = catalogo.marca("Pinho Forte", null);
        // "Pinho" aparece no nome de um, no tipo de nenhum e na marca de outro; "789555" só nos códigos.
        desinfetante = catalogo.produto("Pinho Desinfetante", marca, "desinfetante", "7895550000011", null);
        limpador = catalogo.produto("Limpador Multiuso", marca, "limpeza_geral", "7895550000028", "desinfeta superfícies");
        daMarca = catalogo.produto("Lustra Móveis", marcaPinho, "outro", "7891110000035", null);
        for (int produto : new int[] {desinfetante, limpador, daMarca}) {
            catalogo.vinculo(empresa, produto, "TRUE");
        }
    }

    private ResultActions portal(String termo, String fields) throws Exception {
        var pedido = get("/api/v1/empresas/me/produtos").param("q", termo).header("Authorization", "Bearer " + tokenEmpresa);
        if (fields != null) {
            pedido.param("fields", fields);
        }
        return mvc.perform(pedido);
    }

    private ResultActions app(String termo) throws Exception {
        return mvc.perform(get("/api/v1/produtos").param("q", termo).header("Authorization", "Bearer " + tokenUsuario));
    }

    @Test
    void semFieldsProcuraEmTodosOsCamposInclusiveTipoECodigo() throws Exception {
        portal("pinho", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(desinfetante, daMarca)));
        portal("789555", null).andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(desinfetante, limpador)));
        portal("limpeza geral", null).andExpect(jsonPath("$.itens[*].id", Matchers.contains(limpador)));
    }

    @Test
    void nomeProcuraSoNoNomeDoProduto() throws Exception {
        portal("pinho", "nome").andExpect(jsonPath("$.itens[*].id", Matchers.contains(desinfetante)));
        portal("desinfeta", "nome").andExpect(jsonPath("$.itens[*].id", Matchers.contains(desinfetante)));
    }

    @Test
    void tipoProcuraPeloTipoEscritoPorExtensoESemAcento() throws Exception {
        portal("desinfetante", "tipo").andExpect(jsonPath("$.itens[*].id", Matchers.contains(desinfetante)));
        portal("LIMPEZA", "tipo").andExpect(jsonPath("$.itens[*].id", Matchers.contains(limpador)));
        portal("pinho", "tipo").andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void codBarrasProcuraTrechoDoCodigo() throws Exception {
        portal("789555", "codBarras").andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(desinfetante, limpador)));
        portal("789111", "codBarras").andExpect(jsonPath("$.itens[*].id", Matchers.contains(daMarca)));
        portal("pinho", "codBarras").andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void variosCamposSomamOsResultados() throws Exception {
        portal("desinfet", "nome,tipo").andExpect(jsonPath("$.itens[*].id", Matchers.contains(desinfetante)));
        mvc.perform(get("/api/v1/empresas/me/produtos").param("q", "0000").param("fields", "nome").param("fields", "codBarras")
                        .header("Authorization", "Bearer " + tokenEmpresa))
                .andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(desinfetante, limpador, daMarca)));
    }

    @Test
    void campoForaDaListaDa400() throws Exception {
        for (String invalido : new String[] {"descricao", "Nome", "marca", "nome,senha"}) {
            portal("pinho", invalido).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("campo_busca_invalido"));
        }
    }

    @Test
    void elementoEmBrancoEhIgnorado() throws Exception {
        portal("pinho", "nome,").andExpect(status().isOk()).andExpect(jsonPath("$.itens[*].id", Matchers.contains(desinfetante)));
        portal("pinho", " , ").andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(desinfetante, daMarca)));
    }

    @Test
    void fieldsSemTermoNaoFiltra() throws Exception {
        mvc.perform(get("/api/v1/empresas/me/produtos").param("fields", "nome").header("Authorization", "Bearer " + tokenEmpresa))
                .andExpect(jsonPath("$.totalItens").value(3));
    }

    /** O app continua procurando só em nome, descrição e marca; `fields` não existe nele e é ignorado. */
    @Test
    void buscaDoAppNaoMuda() throws Exception {
        app("pinho").andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(desinfetante, daMarca)));
        app("desinfeta").andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(desinfetante, limpador)));
        app("789555").andExpect(jsonPath("$.totalItens").value(0));
        app("limpeza geral").andExpect(jsonPath("$.totalItens").value(0));
        mvc.perform(get("/api/v1/produtos").param("q", "pinho").param("fields", "senha")
                        .header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].id", Matchers.containsInAnyOrder(desinfetante, daMarca)));
    }
}
