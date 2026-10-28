package com.api.quimia.domain.catalog.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LimiteDeBuscasPorImagemTest {
    private static final Instant INICIO = Instant.parse("2026-10-28T12:00:00Z");
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BIA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

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
        LimiteDeBuscasPorImagem limite = new LimiteDeBuscasPorImagem(relogio, 2);

        assertThat(limite.aceitar(ANA)).isTrue();
        relogio.avancar(Duration.ofMinutes(30));
        assertThat(limite.aceitar(ANA)).isTrue();
        assertThat(limite.aceitar(ANA)).isFalse();

        relogio.avancar(Duration.ofMinutes(30));
        assertThat(limite.aceitar(ANA)).as("uma hora depois a primeira sai da janela").isTrue();
        assertThat(limite.aceitar(ANA)).isFalse();
    }

    @Test
    void cadaUsuarioTemSuaJanela() {
        LimiteDeBuscasPorImagem limite = new LimiteDeBuscasPorImagem(new Relogio(), 1);

        assertThat(limite.aceitar(ANA)).isTrue();
        assertThat(limite.aceitar(ANA)).isFalse();
        assertThat(limite.aceitar(BIA)).isTrue();
    }

    @Test
    void limpezaRemoveSoJanelasVencidas() {
        Relogio relogio = new Relogio();
        LimiteDeBuscasPorImagem limite = new LimiteDeBuscasPorImagem(relogio, 5);
        limite.aceitar(ANA);
        relogio.avancar(Duration.ofMinutes(40));
        limite.aceitar(BIA);

        relogio.avancar(Duration.ofMinutes(20));
        limite.limparJanelasVencidas();

        assertThat(limite.usuariosAcompanhados()).isEqualTo(1);
    }

    @Test
    void limiteTemQueSerPositivo() {
        assertThatThrownBy(() -> new LimiteDeBuscasPorImagem(new Relogio(), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QUIMIA_AGENTE_BUSCAS_POR_IMAGEM_POR_HORA");
    }
}
