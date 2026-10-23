package com.api.quimia.domain.assistant.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LimiteDeMensagensTest {
    private static final Instant INICIO = Instant.parse("2026-10-19T12:00:00Z");

    /** Relógio que o teste avança. */
    private static final class Relogio extends Clock {
        private Instant agora = INICIO;

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void aceitaAteOLimiteNaJanelaDeUmaHoraEDepoisLiberaAMaisAntiga() {
        Relogio relogio = new Relogio();
        LimiteDeMensagens limite = new LimiteDeMensagens(relogio, 3);

        assertThat(limite.aceitar("a")).isTrue();
        relogio.avancar(Duration.ofMinutes(10));
        assertThat(limite.aceitar("a")).isTrue();
        assertThat(limite.aceitar("a")).isTrue();
        assertThat(limite.aceitar("a")).isFalse();

        relogio.avancar(Duration.ofMinutes(50).minusSeconds(1));
        assertThat(limite.aceitar("a")).as("a primeira ainda está dentro da hora").isFalse();
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(limite.aceitar("a")).as("exatamente uma hora depois a primeira sai").isTrue();
        assertThat(limite.aceitar("a")).isFalse();
    }

    @Test
    void recusadaNaoConsomeVagaECadaAutorTemSuaJanela() {
        Relogio relogio = new Relogio();
        LimiteDeMensagens limite = new LimiteDeMensagens(relogio, 1);

        assertThat(limite.aceitar("a")).isTrue();
        for (int i = 0; i < 10; i++) {
            assertThat(limite.aceitar("a")).isFalse();
        }
        assertThat(limite.aceitar("b")).isTrue();
        relogio.avancar(Duration.ofHours(1));
        assertThat(limite.aceitar("a")).isTrue();
    }

    @Test
    void limpezaRemoveSoJanelasVencidas() {
        Relogio relogio = new Relogio();
        LimiteDeMensagens limite = new LimiteDeMensagens(relogio, 5);
        limite.aceitar("antigo");
        relogio.avancar(Duration.ofMinutes(30));
        limite.aceitar("recente");
        relogio.avancar(Duration.ofMinutes(31));

        limite.limparJanelasVencidas();

        assertThat(limite.autoresAcompanhados()).isEqualTo(1);
        relogio.avancar(Duration.ofMinutes(30));
        limite.limparJanelasVencidas();
        assertThat(limite.autoresAcompanhados()).isZero();
    }

    @Test
    void envioSimultaneoNuncaPassaDoLimite() throws Exception {
        LimiteDeMensagens limite = new LimiteDeMensagens(new Relogio(), 20);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> tentativas = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            tentativas.add(pool.submit(() -> {
                largada.await();
                return limite.aceitar("mesmo-autor");
            }));
        }
        largada.countDown();
        int aceitas = 0;
        for (Future<Boolean> tentativa : tentativas) {
            if (tentativa.get(10, TimeUnit.SECONDS)) {
                aceitas++;
            }
        }
        pool.shutdown();

        assertThat(aceitas).isEqualTo(20);
    }

    @Test
    void limiteNaoPositivoImpedeASubida() {
        assertThatThrownBy(() -> new LimiteDeMensagens(new Relogio(), 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LimiteDeMensagens(new Relogio(), -1)).isInstanceOf(IllegalStateException.class);
    }
}
