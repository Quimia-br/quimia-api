package com.api.quimia.domain.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.assistant.internal.client.AgenteKemi;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiDesligado;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiException;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiException.Motivo;
import com.api.quimia.domain.assistant.internal.client.HttpAgenteKemi;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente do quimia-agent contra um servidor HTTP real no loopback. */
class HttpAgenteKemiTest {
    private static final UUID AUTOR = UUID.fromString("0b6f2c1e-1d2e-4f3a-9b4c-5d6e7f8a9b0c");
    private static final Duration PRAZO = Duration.ofMillis(800);

    private AgenteDeTeste servidor;

    @BeforeEach
    void subir() throws IOException {
        servidor = new AgenteDeTeste();
    }

    @AfterEach
    void derrubar() throws IOException {
        servidor.close();
    }

    private static AgenteKemi cliente(String url, String token) {
        return HttpAgenteKemi.criar(url, token, PRAZO);
    }

    private AgenteKemi cliente() {
        return cliente(servidor.url() + "/", "segredo");
    }

    private static void falhaCom(Motivo motivo, Runnable chamada) {
        assertThatThrownBy(chamada::run)
                .isInstanceOfSatisfying(AgenteKemiException.class, erro -> assertThat(erro.motivo()).isEqualTo(motivo));
    }

    @Test
    void enviaSessaoAutorEPerguntaComTokenEDevolveARespostaAparada() {
        servidor.responder(200, "{\"resposta\":\"  Não misture cloro com amônia. 😷  \",\"extra\":1}");

        String resposta = cliente().perguntar(AUTOR, "sessao-1", "Posso misturar \"cloro\" e amônia?\n");

        assertThat(resposta).isEqualTo("Não misture cloro com amônia. 😷");
        AgenteDeTeste.Pedido pedido = servidor.pedidos().get(0);
        assertThat(pedido.metodo()).isEqualTo("POST");
        assertThat(pedido.caminho()).isEqualTo("/chat");
        assertThat(servidor.pedidos()).hasSize(1);
        assertThat(pedido.autorizacao()).isEqualTo("Bearer segredo");
        assertThat(pedido.tipo()).startsWith("application/json");
        JsonNode corpo = JsonMapper.builder().build().readTree(pedido.corpo());
        assertThat(corpo.path("session_id").asString()).isEqualTo("sessao-1");
        assertThat(corpo.path("user_id").asString()).isEqualTo(AUTOR.toString());
        assertThat(corpo.path("pergunta").asString()).isEqualTo("Posso misturar \"cloro\" e amônia?\n");
        assertThat(corpo.size()).isEqualTo(3);
    }

    @Test
    void semTokenNaoMandaAutorizacao() {
        cliente(servidor.url(), "").perguntar(AUTOR, "s", "oi");

        assertThat(servidor.pedidos().get(0).autorizacao()).isNull();
    }

    @Test
    void agenteForaDoArEhIndisponivelEGatewayTimeoutEhTempoEsgotado() {
        servidor.responder(503, "{\"detail\":\"Não foi possível\"}");
        falhaCom(Motivo.INDISPONIVEL, () -> cliente().perguntar(AUTOR, "s", "oi"));

        servidor.responder(504, "");
        falhaCom(Motivo.TEMPO_ESGOTADO, () -> cliente().perguntar(AUTOR, "s", "oi"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 413, 422, 429, 500, 502})
    void outroErroDoAgenteEhFalha(int status) {
        servidor.responder(status, "{\"detail\":\"erro\"}");

        falhaCom(Motivo.FALHOU, () -> cliente().perguntar(AUTOR, "s", "oi"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "não é json", "{", "[]", "null", "\"texto\"", "{}", "{\"resposta\":null}", "{\"resposta\":42}",
        "{\"resposta\":{\"texto\":\"x\"}}", "{\"resposta\":\"\"}", "{\"resposta\":\"   \\n\\t \"}", "{\"Resposta\":\"x\"}"
    })
    void corpoForaDoContratoEhFalha(String corpo) {
        servidor.responder(200, corpo);

        falhaCom(Motivo.FALHOU, () -> cliente().perguntar(AUTOR, "s", "oi"));
    }

    @Test
    void erroDoAgenteNaoViraRespostaMesmoComCorpoValido() {
        servidor.responder(500, "{\"resposta\":\"parece válida\"}");

        falhaCom(Motivo.FALHOU, () -> cliente().perguntar(AUTOR, "s", "oi"));
    }

    @Test
    void corpoNoLimiteDeBytesAceitoEUmByteAMaisRecusado() {
        String json = "{\"resposta\":\"x\"}";
        byte[] noLimite = (json + " ".repeat(256 * 1024 - json.length())).getBytes(StandardCharsets.UTF_8);
        servidor.responder(200, noLimite);
        assertThat(cliente().perguntar(AUTOR, "s", "oi")).isEqualTo("x");

        byte[] acima = (json + " ".repeat(256 * 1024 - json.length() + 1)).getBytes(StandardCharsets.UTF_8);
        servidor.responder(200, acima);
        falhaCom(Motivo.FALHOU, () -> cliente().perguntar(AUTOR, "s", "oi"));
    }

    @Test
    void respostaNoLimiteAceitaEAcimaEhFalha() {
        String noLimite = "a".repeat(20_000);
        servidor.responder(200, "{\"resposta\":\"" + noLimite + "\"}");
        assertThat(cliente().perguntar(AUTOR, "s", "oi")).hasSize(20_000);

        servidor.responder(200, "{\"resposta\":\"" + noLimite + "b\"}");
        falhaCom(Motivo.FALHOU, () -> cliente().perguntar(AUTOR, "s", "oi"));
    }

    @Test
    void corpoGiganteEhRecusadoSemLerTudo() {
        byte[] gigante = new byte[2 * 1024 * 1024];
        Arrays.fill(gigante, (byte) ' ');
        byte[] inicio = "{\"resposta\":\"x\"".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(inicio, 0, gigante, 0, inicio.length);
        gigante[gigante.length - 1] = '}';
        servidor.responder(200, gigante);

        falhaCom(Motivo.FALHOU, () -> cliente().perguntar(AUTOR, "s", "oi"));
    }

    @Test
    void agenteLentoAlemDoPrazoEhTempoEsgotado() {
        servidor.atrasar(PRAZO.multipliedBy(3));

        falhaCom(Motivo.TEMPO_ESGOTADO, () -> cliente().perguntar(AUTOR, "s", "oi"));
    }

    @Test
    void portaSemNinguemEhIndisponivel() throws IOException {
        int portaLivre;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            portaLivre = socket.getLocalPort();
        }

        falhaCom(Motivo.INDISPONIVEL, () -> cliente("http://127.0.0.1:" + portaLivre, "t").perguntar(AUTOR, "s", "oi"));
    }

    @Test
    void desligadoSempreIndisponivel() {
        falhaCom(Motivo.INDISPONIVEL, () -> new AgenteKemiDesligado().perguntar(AUTOR, "s", "oi"));
    }
}
