package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Análise da FDS em segundo plano (`/fds/analises/assincronas`): o host corta respostas após cerca de 30 s, então o
 * portal recebe um id na hora e consulta o estado. O extrator e o armazenamento do PDF são simulados.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({FdsDoProdutoWebTest.ExtratorFalso.class, FdsDoProdutoWebTest.ArmazenamentoFalso.class})
class AnaliseDeFdsAssincronaWebTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String COM_PENDENCIAS = recurso("/fds/resposta-extrator-com-pendencias.json");
    private static final String ANALISES = "/api/v1/empresas/me/produtos/fds/analises/assincronas";
    private static final long ESPERA_MAXIMA_MILISSEGUNDOS = 15_000;
    private static final long INTERVALO_MILISSEGUNDOS = 25;
    /** Igual a `AnalisesEmAndamento.LIMITE_EM_ANDAMENTO`, que é privado ao pacote do caso de uso. */
    private static final int LIMITE_EM_ANDAMENTO = 6;

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

    private static String recurso(String caminho) {
        try (InputStream exemplo = AnaliseDeFdsAssincronaWebTest.class.getResourceAsStream(caminho)) {
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
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@assincrona.test'");
        token = TestLogins.empresa(mvc, "Empresa Assincrona", "e@assincrona.test", "30000035000191");
        tokenOutra = TestLogins.empresa(mvc, "Outra Assincrona", "o@assincrona.test", "30000036000136");
    }

    @AfterEach
    void soltarOExtrator() {
        extrator.limpar();
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("fds", "ficha.pdf", "application/pdf", FdsDoProdutoWebTest.PDF);
    }

    private ResultActions iniciar(String tokenDeQuem, MockMultipartFile... arquivos) throws Exception {
        var pedido = multipart(ANALISES);
        for (MockMultipartFile arquivo : arquivos) {
            pedido.file(arquivo);
        }
        return mvc.perform(pedido.header("Authorization", "Bearer " + tokenDeQuem));
    }

    private ResultActions consultar(String tokenDeQuem, String id) throws Exception {
        return mvc.perform(get(ANALISES + "/" + id).header("Authorization", "Bearer " + tokenDeQuem));
    }

    private String idDaNovaAnalise() throws Exception {
        return corpo(iniciar(token, pdf()).andExpect(status().isAccepted())).path("id").asString();
    }

    private static JsonNode corpo(ResultActions resposta) throws Exception {
        return JSON.readTree(resposta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Consulta até a análise sair de `processando`; falha o teste se passar da espera máxima. */
    private JsonNode aguardarFim(String id) throws Exception {
        long limite = System.currentTimeMillis() + ESPERA_MAXIMA_MILISSEGUNDOS;
        while (System.currentTimeMillis() < limite) {
            JsonNode estado = corpo(consultar(token, id).andExpect(status().isOk()));
            if (!"processando".equals(estado.path("status").asString())) {
                return estado;
            }
            Thread.sleep(INTERVALO_MILISSEGUNDOS);
        }
        throw new AssertionError("a análise " + id + " não terminou em " + ESPERA_MAXIMA_MILISSEGUNDOS + " ms");
    }

    @Test
    void iniciarDevolve202ComIdEAConsultaTerminaComOMesmoCorpoDaAnaliseSincrona() throws Exception {
        extrator.segurar();
        ResultActions aceita = iniciar(token, pdf())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("processando"))
                .andExpect(jsonPath("$.analise").doesNotExist());
        String id = corpo(aceita).path("id").asString();
        extrator.liberar();

        JsonNode terminada = aguardarFim(id);

        assertThat(terminada.path("status").asString()).isEqualTo("concluida");
        JsonNode analise = terminada.path("analise");
        assertThat(analise.path("analiseId").asString()).startsWith("v1.");
        assertThat(analise.path("status").asString()).isEqualTo("processado_com_pendencias");
        assertThat(analise.path("compostos").get(0).path("nome").asString()).isEqualTo("Hipoclorito de Sódio");
        assertThat(analise.path("pendencias")).hasSize(2);
        assertThat(extrator.chamadas()).isEqualTo(1);
        assertThat(documentos.enviados()).hasSize(1);
        assertThat(documentos.apagados()).isEmpty();
    }

    @Test
    void enquantoOExtratorTrabalhaAConsultaDevolveProcessando() throws Exception {
        extrator.segurar();
        String id = idDaNovaAnalise();

        consultar(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("processando"))
                .andExpect(jsonPath("$.analise").doesNotExist());

        extrator.liberar();
        assertThat(aguardarFim(id).path("status").asString()).isEqualTo("concluida");
    }

    @Test
    void analiseDeOutraEmpresaOuIdInventadoEh404() throws Exception {
        String id = idDaNovaAnalise();
        aguardarFim(id);

        consultar(tokenOutra, id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("analise_nao_encontrada"));
        consultar(token, "id-que-nao-existe").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("analise_nao_encontrada"));
    }

    @Test
    void erroDeArquivoSaiNaHoraSemCriarAnalise() throws Exception {
        iniciar(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("fds_ausente"));
        iniciar(token, new MockMultipartFile("fds", "ficha.pdf", "application/pdf", "texto puro".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("pdf_invalido"));

        assertThat(extrator.chamadas()).isZero();
        assertThat(documentos.enviados()).isEmpty();
    }

    @Test
    void semTokenDeEmpresaOEnvioEAConsultaSaoRecusados() throws Exception {
        mvc.perform(multipart(ANALISES).file(pdf())).andExpect(status().isUnauthorized());
        mvc.perform(get(ANALISES + "/qualquer")).andExpect(status().isUnauthorized());
    }

    @Test
    void falhaDoExtratorTerminaFalhouComOMesmoStatusECodigoDaAnaliseSincronaEApagaOPdf() throws Exception {
        extrator.falharComo(ExtratorFdsException.Motivo.FALHOU);
        JsonNode falhou = aguardarFim(idDaNovaAnalise());
        assertThat(falhou.path("status").asString()).isEqualTo("falhou");
        assertThat(falhou.path("codigo").asInt()).isEqualTo(502);
        assertThat(falhou.path("detail").asString()).isEqualTo("extrator_falhou");
        assertThat(falhou.path("analise").isNull()).isTrue();

        extrator.falharComo(ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        JsonNode recusada = aguardarFim(idDaNovaAnalise());
        assertThat(recusada.path("codigo").asInt()).isEqualTo(422);
        assertThat(recusada.path("detail").asString()).isEqualTo("fds_invalida");

        assertThat(documentos.apagados()).containsExactlyElementsOf(documentos.enviados());
    }

    @Test
    void falhaInesperadaDoExtratorTambemTerminaFalhouEmJson() throws Exception {
        extrator.explodirCom(new IllegalStateException("cliente HTTP quebrou"));

        JsonNode falhou = aguardarFim(idDaNovaAnalise());

        assertThat(falhou.path("status").asString()).isEqualTo("falhou");
        assertThat(falhou.path("codigo").asInt()).isEqualTo(502);
        assertThat(falhou.path("detail").asString()).isEqualTo("extrator_falhou");
    }

    @Test
    void acimaDoLimiteEmAndamentoDevolve429() throws Exception {
        extrator.segurar();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < LIMITE_EM_ANDAMENTO; i++) {
            ids.add(idDaNovaAnalise());
        }

        iniciar(token, pdf()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("analises_demais"));

        extrator.liberar();
        for (String id : ids) {
            aguardarFim(id);
        }
    }
}
