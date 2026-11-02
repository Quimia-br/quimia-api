package com.api.quimia.domain.catalog.internal.fds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

class HttpExtratorFdsTest {
    private static final String URL = "https://extrator.test";
    private static final String ROTA = URL + "/extracoes/assincronas";
    private static final String TOKEN = "token-de-teste";
    private static final byte[] PDF = "%PDF-1.7 conteudo".getBytes(StandardCharsets.US_ASCII);
    private static final String PROCESSANDO = "{\"id\":\"abc\",\"status\":\"processando\"}";
    private static final String HTML_DO_HOST = "<html><body>Erro Interno</body></html>";

    private MockRestServiceServer servidor;
    private HttpExtratorFds extrator;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        extrator = new HttpExtratorFds(builder.build(), URL + "/", TOKEN, Duration.ZERO, Duration.ofSeconds(30));
    }

    private static DefaultResponseCreator aceito() {
        return withStatus(HttpStatus.ACCEPTED).body(PROCESSANDO).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void enviaOPdfNoCampoArquivoComTokenEConsultaAteConcluirDevolvendoOResultado() {
        AtomicReference<String> corpo = new AtomicReference<>();
        servidor.expect(requestTo(ROTA))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(request -> corpo.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(aceito());
        servidor.expect(requestTo(ROTA + "/abc"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(PROCESSANDO, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(ROTA + "/abc"))
                .andRespond(withSuccess(
                        "{\"id\":\"abc\",\"status\":\"concluida\",\"resultado\":{\"status\":\"processado\"}}",
                        MediaType.APPLICATION_JSON));

        String resposta = extrator.extrair(PDF);

        servidor.verify();
        assertThat(resposta).isEqualTo("{\"status\":\"processado\"}");
        assertThat(corpo.get()).contains("name=\"arquivo\"", "filename=\"fds.pdf\"", "application/pdf", "%PDF-1.7 conteudo");
    }

    @Test
    void falhaDaExtracaoTraduzOCodigoDaRotaSincrona() {
        esperarFalhaDaExtracao(400, ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        esperarFalhaDaExtracao(422, ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        esperarFalhaDaExtracao(413, ExtratorFdsException.Motivo.MUITO_GRANDE);
        esperarFalhaDaExtracao(502, ExtratorFdsException.Motivo.FALHOU);
        esperarFalhaDaExtracao(504, ExtratorFdsException.Motivo.FALHOU);
        esperarFalhaDaExtracao(500, ExtratorFdsException.Motivo.FALHOU);
    }

    @Test
    void envioRecusadoTemMotivosProprios() {
        esperarEnvioRecusado(HttpStatus.BAD_REQUEST, ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        esperarEnvioRecusado(HttpStatus.UNPROCESSABLE_CONTENT, ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        esperarEnvioRecusado(HttpStatus.CONTENT_TOO_LARGE, ExtratorFdsException.Motivo.MUITO_GRANDE);
        esperarEnvioRecusado(HttpStatus.TOO_MANY_REQUESTS, ExtratorFdsException.Motivo.INDISPONIVEL);
        esperarEnvioRecusado(HttpStatus.SERVICE_UNAVAILABLE, ExtratorFdsException.Motivo.INDISPONIVEL);
        esperarEnvioRecusado(HttpStatus.UNAUTHORIZED, ExtratorFdsException.Motivo.FALHOU);
        esperarEnvioRecusado(HttpStatus.NOT_FOUND, ExtratorFdsException.Motivo.FALHOU);
        esperarEnvioRecusado(HttpStatus.INTERNAL_SERVER_ERROR, ExtratorFdsException.Motivo.FALHOU);
    }

    @Test
    void paginaHtmlComStatus200NoEnvioOuNaConsultaEFalhaDoExtrator() {
        servidor.expect(requestTo(ROTA)).andRespond(withSuccess(HTML_DO_HOST, MediaType.TEXT_HTML));
        esperarMotivo(ExtratorFdsException.Motivo.FALHOU);

        servidor.reset();
        servidor.expect(requestTo(ROTA)).andRespond(aceito());
        servidor.expect(requestTo(ROTA + "/abc")).andRespond(withSuccess(HTML_DO_HOST, MediaType.TEXT_HTML));
        esperarMotivo(ExtratorFdsException.Motivo.FALHOU);
    }

    @Test
    void envioSemIdOuConsultaComEstadoDesconhecidoEFalhaDoExtrator() {
        servidor.expect(requestTo(ROTA))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .body("{\"status\":\"processando\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        esperarMotivo(ExtratorFdsException.Motivo.FALHOU);

        servidor.reset();
        servidor.expect(requestTo(ROTA)).andRespond(aceito());
        servidor.expect(requestTo(ROTA + "/abc"))
                .andRespond(withSuccess("{\"status\":\"estranho\"}", MediaType.APPLICATION_JSON));
        esperarMotivo(ExtratorFdsException.Motivo.FALHOU);
    }

    @Test
    void extracaoPerdidaNoReinicioDoExtratorEFalha() {
        servidor.expect(requestTo(ROTA)).andRespond(aceito());
        servidor.expect(requestTo(ROTA + "/abc")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        esperarMotivo(ExtratorFdsException.Motivo.FALHOU);
    }

    @Test
    void extracaoQueNaoTerminaNoPrazoEIndisponivel() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        extrator = new HttpExtratorFds(builder.build(), URL, TOKEN, Duration.ZERO, Duration.ZERO);
        servidor.expect(requestTo(ROTA)).andRespond(aceito());
        servidor.expect(manyTimes(), requestTo(ROTA + "/abc"))
                .andRespond(withSuccess(PROCESSANDO, MediaType.APPLICATION_JSON));

        esperarMotivo(ExtratorFdsException.Motivo.INDISPONIVEL);
    }

    @Test
    void semConexaoOuSemRespostaEhIndisponivel() {
        servidor.expect(requestTo(ROTA)).andRespond(withException(new ConnectException("recusada")));
        esperarMotivo(ExtratorFdsException.Motivo.INDISPONIVEL);

        servidor.reset();
        servidor.expect(requestTo(ROTA)).andRespond(aceito());
        servidor.expect(requestTo(ROTA + "/abc")).andRespond(withException(new ConnectException("recusada")));
        esperarMotivo(ExtratorFdsException.Motivo.INDISPONIVEL);
    }

    @Test
    void desligadoSempreIndisponivel() {
        assertThatThrownBy(() -> new ExtratorFdsDesligado().extrair(PDF))
                .isInstanceOfSatisfying(ExtratorFdsException.class,
                        erro -> assertThat(erro.motivo()).isEqualTo(ExtratorFdsException.Motivo.INDISPONIVEL));
    }

    @Test
    void configuracaoQueQuebrariaEmUsoNaoSobe() {
        ExtratorFdsConfiguration configuracao = new ExtratorFdsConfiguration();
        assertThatThrownBy(() -> configuracao.extratorFds(URL, " ", 60, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QUIMIA_EXTRATOR_TOKEN");
        assertThatThrownBy(() -> configuracao.extratorFds(URL, TOKEN, 0, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QUIMIA_EXTRATOR_TIMEOUT_SEGUNDOS");
        assertThatThrownBy(() -> configuracao.extratorFds("", "", 60, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QUIMIA_EXTRATOR_URL");
        assertThat(configuracao.extratorFds("", "", 60, false)).isInstanceOf(ExtratorFdsDesligado.class);
    }

    private void esperarMotivo(ExtratorFdsException.Motivo motivo) {
        assertThatThrownBy(() -> extrator.extrair(PDF))
                .isInstanceOfSatisfying(ExtratorFdsException.class, erro -> assertThat(erro.motivo()).isEqualTo(motivo));
    }

    private void esperarEnvioRecusado(HttpStatus status, ExtratorFdsException.Motivo motivo) {
        servidor.reset();
        servidor.expect(requestTo(ROTA)).andRespond(withStatus(status));
        esperarMotivo(motivo);
    }

    private void esperarFalhaDaExtracao(int codigo, ExtratorFdsException.Motivo motivo) {
        servidor.reset();
        servidor.expect(requestTo(ROTA)).andRespond(aceito());
        servidor.expect(requestTo(ROTA + "/abc"))
                .andRespond(withSuccess(
                        "{\"id\":\"abc\",\"status\":\"falhou\",\"codigo\":" + codigo + ",\"detail\":\"x\"}",
                        MediaType.APPLICATION_JSON));
        esperarMotivo(motivo);
    }
}
