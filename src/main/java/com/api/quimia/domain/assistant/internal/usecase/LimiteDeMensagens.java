package com.api.quimia.domain.assistant.internal.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mensagens por hora para cada autor, em janela deslizante. Fica em memória: vale por instância e zera ao reiniciar
 * (o schema não tem onde guardar).
 */
@Component
class LimiteDeMensagens {
    private static final Duration JANELA = Duration.ofHours(1);
    private static final long LIMPEZA_MS = 5 * 60 * 1000L;

    private final Map<String, Deque<Instant>> enviadas = new ConcurrentHashMap<>();
    private final Clock relogio;
    private final int porHora;

    @Autowired
    LimiteDeMensagens(@Value("${quimia.agente.mensagens-por-hora:20}") int porHora) {
        this(Clock.systemUTC(), porHora);
    }

    LimiteDeMensagens(Clock relogio, int porHora) {
        if (porHora <= 0) {
            throw new IllegalStateException("QUIMIA_AGENTE_MENSAGENS_POR_HORA must be positive");
        }
        this.relogio = relogio;
        this.porHora = porHora;
    }

    /** Conta a mensagem e devolve `true` se ela cabe na última hora do autor. */
    boolean aceitar(String autor) {
        Instant agora = relogio.instant();
        AtomicBoolean aceita = new AtomicBoolean(false);
        enviadas.compute(autor, (chave, atual) -> {
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
        for (String autor : enviadas.keySet()) {
            enviadas.computeIfPresent(autor, (chave, janela) -> {
                descartarAntigas(janela, agora);
                return janela.isEmpty() ? null : janela;
            });
        }
    }

    int autoresAcompanhados() {
        return enviadas.size();
    }

    private static void descartarAntigas(Deque<Instant> janela, Instant agora) {
        Instant limite = agora.minus(JANELA);
        while (!janela.isEmpty() && !janela.peekFirst().isAfter(limite)) {
            janela.pollFirst();
        }
    }
}
