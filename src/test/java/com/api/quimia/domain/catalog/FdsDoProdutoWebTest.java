package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFds;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFdsException;
import com.api.quimia.domain.catalog.internal.image.DocumentoStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorageException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** PDF da FDS no cadastro e na alteração do produto, com o extrator simulado. */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({FdsDoProdutoWebTest.ExtratorFalso.class, FdsDoProdutoWebTest.ArmazenamentoFalso.class})
class FdsDoProdutoWebTest {
    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
    private static final int LIMITE_DO_PDF = 10 * 1024 * 1024;
    private static final int ALTERACOES_SIMULTANEAS = 6;

    @TestConfiguration
    static class ExtratorFalso {
        @Bean
        @Primary
        FakeExtratorFds fakeExtratorFds() {
            return new FakeExtratorFds();
        }
    }

    /** PDFs das FDS: o armazenamento real fica desligado nos testes (sem `CLOUDINARY_*`). */
    @TestConfiguration
    public static class ArmazenamentoFalso {
        @Bean
        @Primary
        FakeDocumentoStorage fakeDocumentoStorage() {
            return new FakeDocumentoStorage();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DocumentoStorage documentosBean;

    private FakeDocumentoStorage documentos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExtratorFds extratorBean;

    private FakeExtratorFds extrator;
    private TestCatalogo catalogo;
    private String token;
    private String tokenOutra;
    private int empresa;

    @BeforeEach
    void seed() throws Exception {
        extrator = (FakeExtratorFds) extratorBean;
        extrator.limpar();
        documentos = (FakeDocumentoStorage) documentosBean;
        documentos.limpar();
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@fds.test'");
        token = TestLogins.empresa(mvc, "Empresa Fds", "e@fds.test", "30000013000121");
        tokenOutra = TestLogins.empresa(mvc, "Outra Fds", "o@fds.test", "30000014000176");
        empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@fds.test'", Integer.class);
    }

    static MockMultipartFile pdf(byte[] conteudo) {
        return new MockMultipartFile("fds", "ficha.pdf", "application/pdf", conteudo);
    }

    static ResultActions cadastrar(MockMvc mvc, String token, String codBarras, MockMultipartFile... arquivos)
            throws Exception {
        MockMultipartHttpServletRequestBuilder pedido = multipart("/api/v1/empresas/me/produtos");
        for (MockMultipartFile arquivo : arquivos) {
            pedido.file(arquivo);
        }
        pedido.param("nome", "Produto Com Fds").param("tipo", "desinfetante").header("Authorization", "Bearer " + token);
        if (codBarras != null) {
            pedido.param("codBarras", codBarras);
        }
        return mvc.perform(pedido);
    }

    private ResultActions cadastrar(String codBarras, MockMultipartFile... arquivos) throws Exception {
        return cadastrar(mvc, token, codBarras, arquivos);
    }

    private ResultActions alterar(String tokenDeQuem, int produto, MockMultipartFile... arquivos) throws Exception {
        MockMultipartHttpServletRequestBuilder pedido = multipart("/api/v1/empresas/me/produtos/" + produto);
        pedido.with(requisicao -> {
            requisicao.setMethod("PATCH");
            return requisicao;
        });
        for (MockMultipartFile arquivo : arquivos) {
            pedido.file(arquivo);
        }
        return mvc.perform(pedido.header("Authorization", "Bearer " + tokenDeQuem));
    }

    private static int idDe(ResultActions resposta) throws Exception {
        String corpo = resposta.andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(corpo).path("produto").path("id").asInt();
    }

    private List<Map<String, Object>> fdsDo(int produto) {
        String json = catalogo.postgres() ? "raw_json::text" : "CAST(raw_json AS VARCHAR)";
        return jdbc.queryForList(
                "SELECT id, ativo, data_atualizacao, fonte_url, " + json + " AS json FROM fds WHERE id_produto = ? ORDER BY id",
                produto);
    }

    private int produtosDaEmpresa() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM empresa_produto WHERE id_empresa = ?", Integer.class, empresa);
    }

    @Test
    void produtoNovoComFdsGravaARespostaDoExtratorComoFdsAtiva() throws Exception {
        int produto = idDe(cadastrar("7896660000011", pdf(PDF)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("criado")));

        assertThat(extrator.chamadas()).isEqualTo(1);
        List<Map<String, Object>> fds = fdsDo(produto);
        assertThat(fds).hasSize(1);
        assertThat(fds.get(0).get("ativo")).isEqualTo(true);
        assertThat(fds.get(0).get("data_atualizacao")).isNotNull();
        assertThat((String) fds.get(0).get("json")).contains("Etanol", "Agentes oxidantes fortes");
        assertThat(documentos.enviados()).hasSize(1);
        assertThat(fds.get(0).get("fonte_url")).isEqualTo(documentos.enviados().get(0));
        assertThat(documentos.enviados().get(0)).startsWith(FakeDocumentoStorage.BASE + "fds/" + empresa + "/")
                .endsWith("/ficha.pdf");
    }

    /** O detalhe do produto no portal mostra a ficha com download (Figma: "Ficha de dados de segurança"). */
    @Test
    void empresaBaixaAFdsAtivaDoProduto() throws Exception {
        int produto = idDe(cadastrar("7896660000141", pdf(PDF)).andExpect(status().isCreated()));

        fdsParaDownload(token, produto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtoId").value(produto))
                .andExpect(jsonPath("$.url").value(documentos.enviados().get(0)))
                .andExpect(jsonPath("$.nomeArquivo").value("ficha.pdf"))
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty());

        alterar(token, produto, new MockMultipartFile("fds", "Ficha Nova (v2).PDF", "application/pdf", PDF))
                .andExpect(status().isOk());
        fdsParaDownload(token, produto)
                .andExpect(jsonPath("$.url").value(documentos.enviados().get(1)))
                .andExpect(jsonPath("$.nomeArquivo").value("Ficha-Nova-v2.pdf"));
        assertThat(documentos.apagados()).isEmpty();
    }

    @Test
    void downloadSoParaAEmpresaDoProdutoAtivaENaoParaOApp() throws Exception {
        int produto = idDe(cadastrar("7896660000158", pdf(PDF)).andExpect(status().isCreated()));
        int semFds = idDe(cadastrar("7896660000165").andExpect(status().isCreated()));

        fdsParaDownload(tokenOutra, produto).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_encontrado"));
        fdsParaDownload(token, semFds).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("fds_nao_encontrada"));
        fdsParaDownload(TestLogins.usuario(mvc, "u@fds.test"), produto).andExpect(status().isForbidden());
        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE id = ?", empresa);
        fdsParaDownload(token, produto).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("empresa_inativa"));
    }

    /** FDS carregada por outro meio (sem PDF enviado pelo portal) aparece sem link. */
    @Test
    void fdsSemArquivoVemSemLink() throws Exception {
        int produto = idDe(cadastrar("7896660000172").andExpect(status().isCreated()));
        catalogo.fds(produto, "v1", LocalDate.of(2026, 1, 10), true, null);

        fdsParaDownload(token, produto).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").doesNotExist())
                .andExpect(jsonPath("$.nomeArquivo").doesNotExist())
                .andExpect(jsonPath("$.versao").value("v1"));
    }

    @Test
    void armazenamentoForaDoArNaoCriaProduto() throws Exception {
        documentos.falharComo(ImageStorageException.Motivo.INDISPONIVEL);
        cadastrar("7896660000189", pdf(PDF)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("armazenamento_fds_indisponivel"));
        documentos.falharComo(ImageStorageException.Motivo.FALHOU);
        cadastrar("7896660000189", pdf(PDF)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("armazenamento_fds_falhou"));

        assertThat(produtosDaEmpresa()).isZero();
        assertThat(extrator.chamadas()).isZero();
    }

    @Test
    void downloadComVinculoDesativadoDaPropriaEmpresaEh404() throws Exception {
        int produto = idDe(cadastrar("7896660000196", pdf(PDF)).andExpect(status().isCreated()));
        jdbc.update("UPDATE empresa_produto SET ativo = FALSE WHERE id_empresa = ? AND id_produto = ?", empresa, produto);

        fdsParaDownload(token, produto).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("produto_nao_encontrado"));
    }

    /** `fonte_url` gravada por outro meio: link https vai sem nome; o que não é https não vira link. */
    @Test
    void fonteUrlQueNaoVeioDoPortal() throws Exception {
        int produto = idDe(cadastrar("7896660000202").andExpect(status().isCreated()));
        int fds = catalogo.fds(produto, "v2", LocalDate.of(2026, 2, 1), true, null);

        jdbc.update("UPDATE fds SET fonte_url = 'https://fornecedor.example.com/fichas/limpa%2Gvidros' WHERE id = ?", fds);
        fdsParaDownload(token, produto).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://fornecedor.example.com/fichas/limpa%2Gvidros"))
                .andExpect(jsonPath("$.nomeArquivo").doesNotExist());

        jdbc.update("UPDATE fds SET fonte_url = 'javascript:alert(1)' WHERE id = ?", fds);
        fdsParaDownload(token, produto).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").doesNotExist());
    }

    private ResultActions fdsParaDownload(String tokenDeQuem, int produto) throws Exception {
        return mvc.perform(get("/api/v1/empresas/me/produtos/" + produto + "/fds")
                .header("Authorization", "Bearer " + tokenDeQuem));
    }

    @Test
    void semFdsCadastraComoAntesESemChamarOExtrator() throws Exception {
        int produto = idDe(cadastrar("7896660000028").andExpect(status().isCreated()));

        assertThat(extrator.chamadas()).isZero();
        assertThat(fdsDo(produto)).isEmpty();
    }

    @Test
    void vinculoAProdutoExistenteNaoChamaOExtratorNemTrocaAFds() throws Exception {
        int produto = idDe(cadastrar(mvc, tokenOutra, "7896660000035", pdf(PDF)).andExpect(status().isCreated()));
        extrator.limpar();

        cadastrar("7896660000035", pdf(PDF)).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("vinculado"));

        assertThat(extrator.chamadas()).isZero();
        assertThat(fdsDo(produto)).hasSize(1);
    }

    /** No vínculo a FDS é ignorada: nem um arquivo inválido impede vincular. */
    @Test
    void vinculoIgnoraOArquivoEnviado() throws Exception {
        cadastrar(mvc, tokenOutra, "7896660000103").andExpect(status().isCreated());

        cadastrar("7896660000103", pdf("não é pdf".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("vinculado"));
    }

    @Test
    void arquivoQueNaoEhPdfDa415EGrandeDemaisDa413SemCriarNada() throws Exception {
        cadastrar("7896660000042", pdf("isto não é um pdf".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("pdf_invalido"));
        byte[] grande = new byte[LIMITE_DO_PDF + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);
        cadastrar("7896660000042", pdf(grande))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("pdf_muito_grande"));

        assertThat(extrator.chamadas()).isZero();
        assertThat(produtosDaEmpresa()).isZero();
    }

    @Test
    void falhasDoExtratorViramErroDoContratoSemCriarProduto() throws Exception {
        Object[][] casos = {
            {ExtratorFdsException.Motivo.INDISPONIVEL, 503, "extrator_indisponivel"},
            {ExtratorFdsException.Motivo.FALHOU, 502, "extrator_falhou"},
            {ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO, 422, "fds_invalida"},
            {ExtratorFdsException.Motivo.MUITO_GRANDE, 413, "pdf_muito_grande"},
        };
        for (Object[] caso : casos) {
            extrator.falharComo((ExtratorFdsException.Motivo) caso[0]);
            cadastrar("7896660000059", pdf(PDF))
                    .andExpect(status().is((Integer) caso[1]))
                    .andExpect(jsonPath("$.detail").value(caso[2]));
        }
        assertThat(produtosDaEmpresa()).isZero();
        assertThat(documentos.enviados()).hasSize(casos.length);
        assertThat(documentos.apagados()).containsExactlyElementsOf(documentos.enviados());
    }

    @Test
    void respostaDoExtratorForaDoContratoDa502SemCriarProduto() throws Exception {
        extrator.responder(FakeExtratorFds.RESPOSTA_DE_EXEMPLO.replace("\"alta\"", "\"extrema\""));

        cadastrar("7896660000066", pdf(PDF))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("extrator_falhou"));

        assertThat(produtosDaEmpresa()).isZero();
        assertThat(documentos.apagados()).containsExactlyElementsOf(documentos.enviados());
    }

    @Test
    void alteracaoComFdsTornaANovaAtivaEDesativaAAnterior() throws Exception {
        int produto = idDe(cadastrar("7896660000073", pdf(PDF)).andExpect(status().isCreated()));
        extrator.responder(FakeExtratorFds.RESPOSTA_DE_EXEMPLO.replace("Etanol", "Metanol"));

        alterar(token, produto, pdf(PDF)).andExpect(status().isOk());

        List<Map<String, Object>> fds = fdsDo(produto);
        assertThat(fds).hasSize(2);
        assertThat(fds.get(0).get("ativo")).isEqualTo(false);
        assertThat(fds.get(1).get("ativo")).isEqualTo(true);
        assertThat((String) fds.get(1).get("json")).contains("Metanol");
    }

    @Test
    void alteracaoDeProdutoQueNaoPodeSerAlteradoNaoChamaOExtrator() throws Exception {
        int produto = idDe(cadastrar("7896660000080").andExpect(status().isCreated()));
        alterar(tokenOutra, produto, pdf(PDF)).andExpect(status().isNotFound());

        int outraEmpresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'o@fds.test'", Integer.class);
        catalogo.vinculo(outraEmpresa, produto, "TRUE");
        alterar(token, produto, pdf(PDF)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("produto_compartilhado"));

        assertThat(extrator.chamadas()).isZero();
        assertThat(fdsDo(produto)).isEmpty();
    }

    @Test
    void alteracaoComCampoInvalidoNaoChamaOExtrator() throws Exception {
        int produto = idDe(cadastrar("7896660000110").andExpect(status().isCreated()));
        MockMultipartHttpServletRequestBuilder pedido = multipart("/api/v1/empresas/me/produtos/" + produto);
        pedido.with(requisicao -> {
            requisicao.setMethod("PATCH");
            return requisicao;
        });

        mvc.perform(pedido.file(pdf(PDF)).param("tipo", "veneno").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("tipo_invalido"));

        assertThat(extrator.chamadas()).isZero();
    }

    /** O produto é travado na gravação: alterações simultâneas com FDS deixam uma única FDS ativa. */
    @Test
    void alteracoesSimultaneasComFdsDeixamUmaSoAtiva() throws Exception {
        int produto = idDe(cadastrar("7896660000127").andExpect(status().isCreated()));
        ExecutorService pool = Executors.newFixedThreadPool(ALTERACOES_SIMULTANEAS);
        List<Callable<Integer>> chamadas = new ArrayList<>();
        for (int i = 0; i < ALTERACOES_SIMULTANEAS; i++) {
            chamadas.add(() -> alterar(token, produto, pdf(PDF)).andReturn().getResponse().getStatus());
        }
        try {
            for (Future<Integer> resultado : pool.invokeAll(chamadas)) {
                assertThat(resultado.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(fdsDo(produto)).hasSize(ALTERACOES_SIMULTANEAS);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM fds WHERE id_produto = ? AND ativo = TRUE", Integer.class, produto))
                .isEqualTo(1);
    }

    /** No schema real, o trigger do banco materializa a composição e as incompatibilidades a partir do raw_json. */
    @Test
    void noPostgresOTriggerMaterializaComposicaoEIncompatibilidades() throws Exception {
        assumeTrue(catalogo.postgres(), "o trigger trg_processar_fds_raw_json só existe no schema real");

        int produto = idDe(cadastrar("7896660000097", pdf(PDF)).andExpect(status().isCreated()));

        int fds = (Integer) fdsDo(produto).get(0).get("id");
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(raw_json) FROM fds WHERE id = ?", String.class, fds))
                .isEqualTo("object");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fds_composto WHERE id_fds = ?", Integer.class, fds))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT severidade FROM fds_incompatibilidade WHERE id_fds = ?", String.class, fds))
                .isEqualTo("alta");
    }

    /**
     * Se o banco ainda recusar o JSON (aqui, um trigger de teste que imita a falha do `fn_processar_fds_raw_json`),
     * a culpa é do extrator: 502 e nada gravado, nem produto nem vínculo.
     */
    @Test
    void noPostgresFalhaDoTriggerDa502EDesfazOCadastro() throws Exception {
        assumeTrue(catalogo.postgres(), "o trigger só existe no schema real");
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fn_teste_recusa_fds() RETURNS TRIGGER LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.raw_json ->> 'mensagem' = 'quebrar-trigger' THEN
                        RAISE EXCEPTION 'recusada pelo teste' USING ERRCODE = '22023';
                    END IF;
                    RETURN NEW;
                END;
                $$""");
        jdbc.execute("CREATE TRIGGER trg_teste_recusa_fds BEFORE INSERT ON fds"
                + " FOR EACH ROW EXECUTE FUNCTION fn_teste_recusa_fds()");
        try {
            extrator.responder(FakeExtratorFds.RESPOSTA_DE_EXEMPLO
                    .replace("\"mensagem\": null", "\"mensagem\": \"quebrar-trigger\""));

            cadastrar("7896660000134", pdf(PDF))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.detail").value("extrator_falhou"));

            assertThat(produtosDaEmpresa()).isZero();
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM produto WHERE cod_barras = '7896660000134'", Integer.class))
                    .isZero();
            assertThat(documentos.apagados()).containsExactlyElementsOf(documentos.enviados());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_teste_recusa_fds ON fds");
            jdbc.execute("DROP FUNCTION IF EXISTS fn_teste_recusa_fds()");
        }
    }
}
