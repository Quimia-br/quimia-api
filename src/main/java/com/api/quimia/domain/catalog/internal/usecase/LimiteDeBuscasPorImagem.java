package com.api.quimia.domain.catalog.internal.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Buscas pela foto do rótulo por hora para cada usuário, em janela deslizante: cada busca é uma chamada ao modelo do
 * agente. O limite é folgado para quem escaneia a despensa inteira e só barra o abuso. Fica em memória: vale por
 * instância e zera ao reiniciar, como o limite de mensagens do Kemi.
 */
@Component
class LimiteDeBuscasPorImagem {
    private static final Duration JANELA = Duration.ofHours(1);
    private static final long LIMPEZA_MS = 5 * 60 * 1000L;

    private final Map<UUID, Deque<Instant>> feitas = new ConcurrentHashMap<>();
    private final Clock relogio;
    private final int porHora;

    @Autowired
    LimiteDeBuscasPorImagem(@Value("${quimia.agente.buscas-por-imagem-por-hora:60}") int porHora) {
        this(Clock.systemUTC(), porHora);
    }

    LimiteDeBuscasPorImagem(Clock relogio, int porHora) {
        if (porHora <= 0) {
            throw new IllegalStateException("QUIMIA_AGENTE_BUSCAS_POR_IMAGEM_POR_HORA must be positive");
        }
        this.relogio = relogio;
        this.porHora = porHora;
    }

    /** Conta a busca e devolve `true` se ela cabe na última hora do usuário. */
    boolean aceitar(UUID usuarioId) {
        Instant agora = relogio.instant();
        AtomicBoolean aceita = new AtomicBoolean(false);
        feitas.compute(usuarioId, (chave, atual) -> {
            Deque<Instant> janela = atual == null ? new ArrayDeque<>() : atual;
            descartarAntigas(janela, agora);
            if (janela.size() < porHora) {
                janela.addLast(agora);
                aceita.set(true);
            }
            return janela;
        });
        return aceita.get();
    }

    @Scheduled(fixedDelay = LIMPEZA_MS)
    void limparJanelasVencidas() {
        Instant agora = relogio.instant();
        for (UUID usuarioId : feitas.keySet()) {
            feitas.computeIfPresent(usuarioId, (chave, janela) -> {
                descartarAntigas(janela, agora);
                return janela.isEmpty() ? null : janela;
            });
        }
    }

    int usuariosAcompanhados() {
        return feitas.size();
    }

    private static void descartarAntigas(Deque<Instant> janela, Instant agora) {
        Instant limite = agora.minus(JANELA);
        while (!janela.isEmpty() && !janela.peekFirst().isAfter(limite)) {
            janela.pollFirst();
        }
    }
}
