package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.assistant.AgenteDeTeste;
import com.api.quimia.domain.catalog.internal.rotulo.CandidatoDoRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.HttpLeitorDeRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotuloDesligado;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotuloException;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotuloException.Motivo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Cliente do quimia-agent na busca por rótulo, contra um servidor HTTP real no loopback. */
class HttpLeitorDeRotuloTest {
    private static final byte[] IMAGEM = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final Duration PRAZO = Duration.ofMillis(800);
    private static final int CORPO_MAXIMO = 256 * 1024;
    private static final int CANDIDATOS_MAXIMOS = 10;

    private AgenteDeTeste servidor;

    @BeforeEach
    void subir() throws IOException {
        servidor = new AgenteDeTeste();
    }

    @AfterEach
    void derrubar() throws IOException {
        servidor.close();
    }

    private LeitorDeRotulo leitor() {
        return HttpLeitorDeRotulo.criar(servidor.url() + "/", "segredo", PRAZO);
    }

    private static void falhaCom(Motivo motivo, Runnable chamada) {
        assertThatThrownBy(chamada::run).isInstanceOfSatisfying(
                LeitorDeRotuloException.class, erro -> assertThat(erro.motivo()).isEqualTo(motivo));
    }

    @Test
    void enviaAImagemEmMultipartComTokenEDevolveOsCandidatos() {
        servidor.responder(200, """
                {"candidatos":[
                  {"nome":"  Água Sanitária  ","marca":"Marca X","cod_barras":"7891000100103","extra":1},
                  {"nome":"Desinfetante","marca":null},
                  {"nome":"Limpa vidros","marca":"  "}]}
                """);

        List<CandidatoDoRotulo> lidos = leitor().identificar(IMAGEM);

        assertThat(lidos).containsExactly(
                new CandidatoDoRotulo("Água Sanitária", "Marca X", "7891000100103"),
                new CandidatoDoRotulo("Desinfetante", null, null),
                new CandidatoDoRotulo("Limpa vidros", null, null));
        AgenteDeTeste.Pedido pedido = servidor.pedidos().get(0);
        assertThat(pedido.metodo()).isEqualTo("POST");
        assertThat(pedido.caminho()).isEqualTo("/produtos/busca-por-imagem");
        assertThat(pedido.autorizacao()).isEqualTo("Bearer segredo");
        assertThat(pedido.tipo()).startsWith("multipart/form-data");
        assertThat(pedido.corpo()).contains("name=\"imagem\"").contains("PNG");
    }

    @Test
    void listaVaziaEhValidaESemTokenNaoMandaAutorizacao() {
        servidor.responder(200, "{\"candidatos\":[]}");

        assertThat(HttpLeitorDeRotulo.criar(servidor.url(), "", PRAZO).identificar(IMAGEM)).isEmpty();
        assertThat(servidor.pedidos().get(0).autorizacao()).isNull();
    }

    @Test
    void candidatosAlemDoMaximoSaoIgnorados() {
        StringBuilder candidatos = new StringBuilder();
        for (int i = 0; i < CANDIDATOS_MAXIMOS + 5; i++) {
            candidatos.append(i == 0 ? "" : ",").append("{\"nome\":\"Produto ").append(i).append("\"}");
        }
        servidor.responder(200, "{\"candidatos\":[" + candidatos + "]}");

        List<CandidatoDoRotulo> lidos = leitor().identificar(IMAGEM);

        assertThat(lidos).hasSize(CANDIDATOS_MAXIMOS);
        assertThat(lidos.get(CANDIDATOS_MAXIMOS - 1).nome()).isEqualTo("Produto 9");
    }

    @Test
    void agenteForaDoArEhIndisponivelEGatewayTimeoutEhTempoEsgotado() {
        servidor.responder(503, "{\"detail\":\"fora\"}");
        falhaCom(Motivo.INDISPONIVEL, () -> leitor().identificar(IMAGEM));

        servidor.responder(504, "");
        falhaCom(Motivo.TEMPO_ESGOTADO, () -> leitor().identificar(IMAGEM));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 413, 422, 429, 500, 502})
    void outroErroDoAgenteEhFalha(int status) {
        servidor.responder(status, "{\"candidatos\":[{\"nome\":\"parece válido\"}]}");

        falhaCom(Motivo.FALHOU, () -> leitor().identificar(IMAGEM));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "não é json", "{", "[]", "null", "{}", "{\"candidatos\":null}", "{\"candidatos\":{}}",
        "{\"candidatos\":\"x\"}", "{\"candidatos\":[{}]}", "{\"candidatos\":[{\"nome\":null}]}",
        "{\"candidatos\":[{\"nome\":42}]}", "{\"candidatos\":[{\"nome\":\"  \"}]}", "{\"candidatos\":[\"Produto\"]}",
        "{\"Candidatos\":[]}"
    })
    void corpoForaDoContratoEhFalha(String corpo) {
        servidor.responder(200, corpo);

        falhaCom(Motivo.FALHOU, () -> leitor().identificar(IMAGEM));
    }

    @Test
    void corpoNoLimiteDeBytesAceitoEUmByteAMaisRecusado() {
        String json = "{\"candidatos\":[]}";
        servidor.responder(200, (json + " ".repeat(CORPO_MAXIMO - json.length())).getBytes(StandardCharsets.UTF_8));
        assertThat(leitor().identificar(IMAGEM)).isEmpty();

        servidor.responder(200, (json + " ".repeat(CORPO_MAXIMO - json.length() + 1)).getBytes(StandardCharsets.UTF_8));
        falhaCom(Motivo.FALHOU, () -> leitor().identificar(IMAGEM));
    }

    @Test
    void agenteLentoAlemDoPrazoEhTempoEsgotado() {
        servidor.atrasar(PRAZO.multipliedBy(3));

        falhaCom(Motivo.TEMPO_ESGOTADO, () -> leitor().identificar(IMAGEM));
    }

    @Test
    void portaSemNinguemEhIndisponivel() throws IOException {
        int portaLivre;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            portaLivre = socket.getLocalPort();
        }

        falhaCom(Motivo.INDISPONIVEL,
                () -> HttpLeitorDeRotulo.criar("http://127.0.0.1:" + portaLivre, "t", PRAZO).identificar(IMAGEM));
    }

    @Test
    void desligadoSempreIndisponivel() {
        falhaCom(Motivo.INDISPONIVEL, () -> new LeitorDeRotuloDesligado().identificar(IMAGEM));
    }
}
