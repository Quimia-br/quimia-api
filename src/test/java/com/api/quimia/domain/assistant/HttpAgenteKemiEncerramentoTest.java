package com.api.quimia.domain.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.assistant.internal.client.AgenteKemi;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiDesligado;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiException;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiException.Motivo;
import com.api.quimia.domain.assistant.internal.client.HttpAgenteKemi;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** `POST /chat/sessions/close` do quimia-agent contra um servidor HTTP real no loopback. */
class HttpAgenteKemiEncerramentoTest {
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

    private AgenteKemi cliente() {
        return HttpAgenteKemi.criar(servidor.url() + "/", "segredo", PRAZO);
    }

    private static void falhaCom(Motivo motivo, Runnable chamada) {
        assertThatThrownBy(chamada::run)
                .isInstanceOfSatisfying(AgenteKemiException.class, erro -> assertThat(erro.motivo()).isEqualTo(motivo));
    }

    @Test
    void enviaSessaoEAutorComTokenEDevolveASituacao() {
        servidor.responder(200, "{\"session_id\":\"sessao-1\",\"status\":\"resumida\",\"extra\":1}");

        String situacao = cliente().encerrarSessao(AUTOR, "sessao-1");

        assertThat(situacao).isEqualTo(AgenteKemi.RESUMIDA);
        assertThat(servidor.pedidos()).hasSize(1);
        AgenteDeTeste.Pedido pedido = servidor.pedidos().get(0);
        assertThat(pedido.metodo()).isEqualTo("POST");
        assertThat(pedido.caminho()).isEqualTo("/chat/sessions/close");
        assertThat(pedido.autorizacao()).isEqualTo("Bearer segredo");
        assertThat(pedido.tipo()).startsWith("application/json");
        JsonNode corpo = JsonMapper.builder().build().readTree(pedido.corpo());
        assertThat(corpo.path("session_id").asString()).isEqualTo("sessao-1");
        assertThat(corpo.path("user_id").asString()).isEqualTo(AUTOR.toString());
        assertThat(corpo.size()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {AgenteKemi.RESUMIDA, AgenteKemi.JA_RESUMIDA, AgenteKemi.SEM_TURNOS})
    void aceitaAsTresSituacoesDoContrato(String esperada) {
        servidor.responder(200, "{\"session_id\":\"s\",\"status\":\"" + esperada + "\"}");

        assertThat(cliente().encerrarSessao(AUTOR, "s")).isEqualTo(esperada);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":1}", "{\"status\":\"outra\"}", "{\"status\":\"RESUMIDA\"}",
        "não é json", "[]"})
    void respostaForaDoContratoEFalhaDoAgente(String corpo) {
        servidor.responder(200, corpo);

        falhaCom(Motivo.FALHOU, () -> cliente().encerrarSessao(AUTOR, "s"));
    }

    @Test
    void erroDoAgenteViraFalhaIndisponibilidadeOuTempoEsgotado() {
        servidor.responder(500, "{\"detail\":\"erro\"}");
        falhaCom(Motivo.FALHOU, () -> cliente().encerrarSessao(AUTOR, "s"));
        servidor.responder(503, "{\"detail\":\"fora\"}");
        falhaCom(Motivo.INDISPONIVEL, () -> cliente().encerrarSessao(AUTOR, "s"));
        servidor.responder(504, "{\"detail\":\"lento\"}");
        falhaCom(Motivo.TEMPO_ESGOTADO, () -> cliente().encerrarSessao(AUTOR, "s"));
    }

    @Test
    void agenteQueNaoRespondeNoPrazoEstouraOTempo() {
        servidor.responder(200, "{\"status\":\"resumida\"}").atrasar(PRAZO.multipliedBy(3));

        falhaCom(Motivo.TEMPO_ESGOTADO, () -> cliente().encerrarSessao(AUTOR, "s"));
    }

    @Test
    void agenteDesligadoEstaIndisponivel() {
        falhaCom(Motivo.INDISPONIVEL, () -> new AgenteKemiDesligado().encerrarSessao(AUTOR, "s"));
    }
}
