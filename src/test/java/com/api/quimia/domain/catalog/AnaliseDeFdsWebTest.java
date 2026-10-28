package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFds;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFdsException;
import com.api.quimia.domain.catalog.internal.image.DocumentoStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Revisão da FDS antes do cadastro (popups "Estamos analisando sua FDS" e "Pendências identificadas"), com o extrator e
 * o armazenamento do PDF simulados. A resposta do extrator vem do exemplo real, com pendências.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({FdsDoProdutoWebTest.ExtratorFalso.class, FdsDoProdutoWebTest.ArmazenamentoFalso.class})
class AnaliseDeFdsWebTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String COM_PENDENCIAS = recurso("/fds/resposta-extrator-com-pendencias.json");
    private static final String ANALISES = "/api/v1/empresas/me/produtos/fds/analises";
    private static final String UNIDADE_EM_PERCENTUAL = "[{\"indice\":0,\"unidadeConcentracao\":\"%\"}]";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DocumentoStorage documentosBean;

    @Autowired
    private ExtratorFds extratorBean;

    @Autowired
    private JdbcTemplate jdbc;

    private FakeDocumentoStorage documentos;
    private FakeExtratorFds extrator;
    private TestCatalogo catalogo;
    private String token;
    private String tokenOutra;
    private int empresa;

    private static String recurso(String caminho) {
        try (InputStream exemplo = AnaliseDeFdsWebTest.class.getResourceAsStream(caminho)) {
            return new String(exemplo.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException falha) {
            throw new IllegalStateException(falha);
        }
    }

    @BeforeEach
    void seed() throws Exception {
        extrator = (FakeExtratorFds) extratorBean;
        extrator.limpar();
        extrator.responder(COM_PENDENCIAS);
        documentos = (FakeDocumentoStorage) documentosBean;
        documentos.limpar();
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@analise.test'");
        token = TestLogins.empresa(mvc, "Empresa Analise", "e@analise.test", "30000033000100");
        tokenOutra = TestLogins.empresa(mvc, "Outra Analise", "o@analise.test", "30000034000147");
        empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@analise.test'", Integer.class);
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("fds", "ficha.pdf", "application/pdf", FdsDoProdutoWebTest.PDF);
    }

    private ResultActions analisar(String tokenDeQuem, String analiseAnterior, MockMultipartFile... arquivos)
            throws Exception {
        MockMultipartHttpServletRequestBuilder pedido = multipart(ANALISES);
        for (MockMultipartFile arquivo : arquivos) {
            pedido.file(arquivo);
        }
        if (analiseAnterior != null) {
            pedido.param("analiseAnterior", analiseAnterior);
        }
        return mvc.perform(pedido.header("Authorization", "Bearer " + tokenDeQuem));
    }

    private String novaAnalise() throws Exception {
        return JSON.readTree(analisar(token, null, pdf()).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("analiseId").asString();
    }

    private ResultActions cadastrar(String tokenDeQuem, String codBarras, String analiseFds, String correcoes,
            MockMultipartFile... arquivos) throws Exception {
        MockMultipartHttpServletRequestBuilder pedido = multipart("/api/v1/empresas/me/produtos");
        for (MockMultipartFile arquivo : arquivos) {
            pedido.file(arquivo);
        }
        pedido.param("nome", "Produto Analisado").param("tipo", "desinfetante").param("codBarras", codBarras);
        if (analiseFds != null) {
            pedido.param("analiseFds", analiseFds);
        }
        if (correcoes != null) {
            pedido.param("correcoesFds", correcoes);
        }
        return mvc.perform(pedido.header("Authorization", "Bearer " + tokenDeQuem));
    }

    private int produtosDaEmpresa() {
        return jdbc.queryForObject("SELECT count(*) FROM empresa_produto WHERE id_empresa = ?", Integer.class, empresa);
    }

    private int totalDeFds() {
        return jdbc.queryForObject("SELECT count(*) FROM fds", Integer.class);
    }

    private List<Map<String, Object>> fdsDo(int produto) {
        String json = catalogo.postgres() ? "raw_json::text" : "CAST(raw_json AS VARCHAR)";
        return jdbc.queryForList(
                "SELECT ativo, fonte_url, " + json + " AS json FROM fds WHERE id_produto = ? ORDER BY id", produto);
    }

    private JsonNode fdsAtivaDo(int produto) {
        List<Map<String, Object>> fds = fdsDo(produto);
        assertThat(fds).hasSize(1);
        return JSON.readTree((String) fds.get(0).get("json"));
    }

    private static int idDe(ResultActions resposta) throws Exception {
        return JSON.readTree(resposta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("produto").path("id").asInt();
    }

    @Test
    void analisarDevolveAsPendenciasSemCadastrarNada() throws Exception {
        int fdsAntes = totalDeFds();

        analisar(token, null, pdf())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analiseId").value(org.hamcrest.Matchers.startsWith("v1.")))
                .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                .andExpect(jsonPath("$.status").value("processado_com_pendencias"))
                .andExpect(jsonPath("$.mensagem").value("Informações ausentes, verifique o documento anexado"))
                .andExpect(jsonPath("$.compostos[0].indice").value(0))
                .andExpect(jsonPath("$.compostos[0].nome").value("Hipoclorito de Sódio"))
                .andExpect(jsonPath("$.compostos[0].casNumero").value("7681-52-9"))
                .andExpect(jsonPath("$.compostos[0].concentracaoMin").value(2))
                .andExpect(jsonPath("$.compostos[0].concentracaoMax").value(2.5))
                .andExpect(jsonPath("$.compostos[0].unidadeConcentracao").doesNotExist())
                .andExpect(jsonPath("$.pendencias.length()").value(2))
                .andExpect(jsonPath("$.pendencias[0].secao").value("03"))
                .andExpect(jsonPath("$.pendencias[0].campo").value("unidade_concentracao"))
                .andExpect(jsonPath("$.pendencias[0].motivo").value("informacao_ausente"))
                .andExpect(jsonPath("$.pendencias[0].corrigivel").value(true))
                .andExpect(jsonPath("$.pendencias[0].composto.indice").value(0))
                .andExpect(jsonPath("$.pendencias[0].composto.nome").value("Hipoclorito de Sódio"))
                .andExpect(jsonPath("$.pendencias[0].evidencias[0].pagina").value(2))
                .andExpect(jsonPath("$.pendencias[0].evidencias[0].textoEvidencia")
                        .value("Hipoclorito de Sódio 7681-52-9 2 – 2,5 NaClO"))
                .andExpect(jsonPath("$.pendencias[1].secao").value("10"))
                .andExpect(jsonPath("$.pendencias[1].corrigivel").value(false))
                .andExpect(jsonPath("$.pendencias[1].composto").doesNotExist());

        assertThat(extrator.chamadas()).isEqualTo(1);
        assertThat(documentos.enviados()).hasSize(1);
        assertThat(documentos.apagados()).isEmpty();
        assertThat(produtosDaEmpresa()).isZero();
        assertThat(totalDeFds()).isEqualTo(fdsAntes);
    }

    @Test
    void adicionarMesmoAssimGravaAFdsComOCampoNuloENaoChamaOExtratorDeNovo() throws Exception {
        String analise = novaAnalise();

        int produto = idDe(cadastrar(token, "7896670000011", analise, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("criado")));

        assertThat(extrator.chamadas()).isEqualTo(1);
        assertThat(documentos.enviados()).hasSize(1);
        assertThat(documentos.apagados()).isEmpty();
        assertThat(fdsDo(produto).get(0).get("fonte_url")).isEqualTo(documentos.enviados().get(0));
        JsonNode fds = fdsAtivaDo(produto);
        assertThat(fds.at("/secoes/03/compostos/0/unidade_concentracao").isNull()).isTrue();
        assertThat(fds.path("pendencias")).hasSize(2);
        assertThat(fds.path("status").asString()).isEqualTo("processado_com_pendencias");
    }

    @Test
    void correcaoDaEmpresaPreencheAUnidadeETiraAPendenciaResolvida() throws Exception {
        String analise = novaAnalise();

        int produto = idDe(cadastrar(token, "7896670000028", analise, UNIDADE_EM_PERCENTUAL)
                .andExpect(status().isCreated()));

        JsonNode fds = fdsAtivaDo(produto);
        assertThat(fds.at("/secoes/03/compostos/0/unidade_concentracao").asString()).isEqualTo("%");
        assertThat(fds.path("pendencias")).hasSize(1);
        assertThat(fds.at("/pendencias/0/secao").asString()).isEqualTo("10");
        if (catalogo.postgres()) {
            // Desde 2026-10-27 a function do banco copia a unidade para a coluna (antes só ficava no raw_json).
            assertThat(jdbc.queryForObject(
                            "SELECT fc.unidade_concentracao FROM fds_composto fc JOIN fds f ON f.id = fc.id_fds "
                                    + "WHERE f.id_produto = ?",
                            String.class, produto))
                    .isEqualTo("%");
        }
    }

    @Test
    void correcaoInvalidaDa422MantemOPdfParaTentarDeNovo() throws Exception {
        String analise = novaAnalise();
        for (String correcao : new String[] {
            "[{\"indice\":3,\"unidadeConcentracao\":\"%\"}]", "[{\"indice\":0,\"unidadeConcentracao\":\"  \"}]",
            "[{\"indice\":0,\"unidadeConcentracao\":\"" + "x".repeat(21) + "\"}]",
            "[{\"indice\":0,\"unidadeConcentracao\":\"%\"},{\"indice\":0,\"unidadeConcentracao\":\"g/L\"}]",
            "isto não é json", "{\"indice\":0}"
        }) {
            cadastrar(token, "7896670000035", analise, correcao).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("correcao_invalida"));
        }

        assertThat(produtosDaEmpresa()).isZero();
        assertThat(documentos.apagados()).isEmpty();
        cadastrar(token, "7896670000035", analise, UNIDADE_EM_PERCENTUAL).andExpect(status().isCreated());
    }

    @Test
    void naoSobrescreveUnidadeQueODocumentoJaInformou() throws Exception {
        extrator.responder(FakeExtratorFds.RESPOSTA_DE_EXEMPLO);
        String analise = novaAnalise();

        cadastrar(token, "7896670000042", analise, "[{\"indice\":0,\"unidadeConcentracao\":\"g/L\"}]")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("correcao_invalida"));
    }

    @Test
    void pdfEAnaliseJuntosOuCorrecoesSemAnaliseSaoRecusados() throws Exception {
        String analise = novaAnalise();

        cadastrar(token, "7896670000059", analise, null, FdsDoProdutoWebTest.pdf(FdsDoProdutoWebTest.PDF))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("fds_e_analise_juntas"));
        cadastrar(token, "7896670000059", null, UNIDADE_EM_PERCENTUAL).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("correcoes_sem_analise"));

        assertThat(produtosDaEmpresa()).isZero();
        assertThat(extrator.chamadas()).isEqualTo(1);
    }

    @Test
    void analiseDeOutraEmpresaAdulteradaOuInventadaEhRecusada() throws Exception {
        String analise = novaAnalise();
        String[] partes = analise.split("\\.");
        char primeira = partes[1].charAt(10);
        String adulterada = partes[0] + "." + partes[1].substring(0, 10) + (primeira == 'A' ? 'B' : 'A')
                + partes[1].substring(11) + "." + partes[2];

        for (String invalida : new String[] {adulterada, "isto-nao-eh-uma-analise", "v1.a.b"}) {
            cadastrar(token, "7896670000066", invalida, null).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("analise_invalida"));
        }
        cadastrar(tokenOutra, "7896670000066", analise, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("analise_invalida"));

        assertThat(produtosDaEmpresa()).isZero();
        assertThat(documentos.apagados()).isEmpty();
    }

    @Test
    void codigoJaCadastradoSoVinculaEApagaOPdfDaAnalise() throws Exception {
        int existente = idDe(cadastrar(tokenOutra, "7896670000073", null, null).andExpect(status().isCreated()));
        String analise = novaAnalise();

        cadastrar(token, "7896670000073", analise, UNIDADE_EM_PERCENTUAL).andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("vinculado"));

        assertThat(documentos.apagados()).containsExactlyElementsOf(documentos.enviados());
        assertThat(fdsDo(existente)).isEmpty();
    }

    @Test
    void substituirDocumentoApagaOPdfAnteriorSoDepoisDeALeituraNovaDarCerto() throws Exception {
        String primeira = novaAnalise();
        extrator.falharComo(ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);

        analisar(token, primeira, pdf()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("fds_invalida"));
        assertThat(documentos.apagados()).containsExactly(documentos.enviados().get(1));

        extrator.limpar();
        extrator.responder(COM_PENDENCIAS);
        analisar(token, primeira, pdf()).andExpect(status().isOk());

        assertThat(documentos.enviados()).hasSize(3);
        assertThat(documentos.apagados()).containsExactly(documentos.enviados().get(1), documentos.enviados().get(0));
    }

    @Test
    void descartarApagaOPdfDaEmpresaEIgnoraAnaliseAlheiaOuInvalida() throws Exception {
        String analise = novaAnalise();

        mvc.perform(descartar(tokenOutra, analise)).andExpect(status().isNoContent());
        mvc.perform(descartar(token, "lixo")).andExpect(status().isNoContent());
        assertThat(documentos.apagados()).isEmpty();

        mvc.perform(descartar(token, analise)).andExpect(status().isNoContent());
        assertThat(documentos.apagados()).containsExactly(documentos.enviados().get(0));

        mvc.perform(post(ANALISES + "/descartar").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"analiseId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alterarProdutoComAnaliseGravaAFdsNovaComAsCorrecoes() throws Exception {
        int produto = idDe(cadastrar(token, "7896670000080", null, null).andExpect(status().isCreated()));
        String analise = novaAnalise();

        MockMultipartHttpServletRequestBuilder pedido = multipart("/api/v1/empresas/me/produtos/" + produto);
        pedido.with(requisicao -> {
            requisicao.setMethod("PATCH");
            return requisicao;
        });
        pedido.param("analiseFds", analise).param("correcoesFds", UNIDADE_EM_PERCENTUAL);
        mvc.perform(pedido.header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        assertThat(fdsAtivaDo(produto).at("/secoes/03/compostos/0/unidade_concentracao").asString()).isEqualTo("%");
        assertThat(fdsDo(produto).get(0).get("fonte_url")).isEqualTo(documentos.enviados().get(0));
        assertThat(documentos.apagados()).isEmpty();
    }

    @Test
    void analisarExigeOArquivoUmPdfEUmaEmpresaAtiva() throws Exception {
        analisar(token, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("fds_ausente"));
        analisar(token, null, new MockMultipartFile("fds", "x.pdf", "application/pdf", "não é pdf".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.detail").value("pdf_invalido"));
        analisar(TestLogins.usuario(mvc, "u@analise.test"), null, pdf()).andExpect(status().isForbidden());
        mvc.perform(multipart(ANALISES).file(pdf())).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE id = ?", empresa);
        analisar(token, null, pdf()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("empresa_inativa"));

        assertThat(extrator.chamadas()).isZero();
        assertThat(documentos.enviados()).isEmpty();
    }

    @Test
    void falhaDoExtratorNaAnaliseApagaOPdfEResponde502Ou503() throws Exception {
        extrator.falharComo(ExtratorFdsException.Motivo.FALHOU);
        analisar(token, null, pdf()).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("extrator_falhou"));
        extrator.falharComo(ExtratorFdsException.Motivo.INDISPONIVEL);
        analisar(token, null, pdf()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("extrator_indisponivel"));

        assertThat(documentos.apagados()).containsExactlyElementsOf(documentos.enviados());
    }

    private org.springframework.test.web.servlet.RequestBuilder descartar(String tokenDeQuem, String analiseId) {
        return post(ANALISES + "/descartar").header("Authorization", "Bearer " + tokenDeQuem)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"analiseId\":\"" + analiseId + "\"}");
    }
}
