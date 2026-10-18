package com.api.quimia.domain.catalog.internal.fds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpExtratorFdsTest {
    private static final String URL = "https://extrator.test";
    private static final String TOKEN = "token-de-teste";
    private static final byte[] PDF = "%PDF-1.7 conteudo".getBytes(StandardCharsets.US_ASCII);

    private MockRestServiceServer servidor;
    private HttpExtratorFds extrator;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        extrator = new HttpExtratorFds(builder.build(), URL + "/", TOKEN);
    }

    @Test
    void enviaOPdfNoCampoArquivoComTokenEDevolveOCorpo() {
        AtomicReference<String> corpo = new AtomicReference<>();
        servidor.expect(requestTo(URL + "/extracoes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(request -> corpo.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"status\":\"processado\"}", MediaType.APPLICATION_JSON));

        String resposta = extrator.extrair(PDF);

        servidor.verify();
        assertThat(resposta).isEqualTo("{\"status\":\"processado\"}");
        assertThat(corpo.get()).contains("name=\"arquivo\"", "filename=\"fds.pdf\"", "application/pdf", "%PDF-1.7 conteudo");
    }

    @Test
    void documentoRecusadoTamanhoEErroDoExtratorTemMotivosProprios() {
        esperarMotivo(HttpStatus.BAD_REQUEST, ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        esperarMotivo(HttpStatus.UNPROCESSABLE_CONTENT, ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        esperarMotivo(HttpStatus.CONTENT_TOO_LARGE, ExtratorFdsException.Motivo.MUITO_GRANDE);
        esperarMotivo(HttpStatus.UNAUTHORIZED, ExtratorFdsException.Motivo.FALHOU);
        esperarMotivo(HttpStatus.INTERNAL_SERVER_ERROR, ExtratorFdsException.Motivo.FALHOU);
    }

    @Test
    void semConexaoOuSemRespostaEhIndisponivel() {
        servidor.expect(requestTo(URL + "/extracoes")).andRespond(withException(new ConnectException("recusada")));

        assertThatThrownBy(() -> extrator.extrair(PDF))
                .isInstanceOfSatisfying(ExtratorFdsException.class,
                        erro -> assertThat(erro.motivo()).isEqualTo(ExtratorFdsException.Motivo.INDISPONIVEL));
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

    private void esperarMotivo(HttpStatus status, ExtratorFdsException.Motivo motivo) {
        servidor.reset();
        servidor.expect(requestTo(URL + "/extracoes")).andRespond(withStatus(status));

        assertThatThrownBy(() -> extrator.extrair(PDF))
                .isInstanceOfSatisfying(ExtratorFdsException.class, erro -> assertThat(erro.motivo()).isEqualTo(motivo));
    }
}
