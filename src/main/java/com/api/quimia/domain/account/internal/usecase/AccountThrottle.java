package com.api.quimia.domain.account.internal.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Contadores em memória para bloqueio por falhas e limites de solicitação. O schema não tem
 * colunas para isso: o estado vale por instância e é perdido ao reiniciar a aplicação.
 */
@Component
public class AccountThrottle {
    private static final Duration REQUEST_WINDOW = Duration.ofHours(1);
    private static final long PURGE_INTERVAL_MS = 5 * 60 * 1000L;

    private final Map<String, FailureWindow> failures = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public AccountThrottle() {
        this(Clock.systemUTC());
    }

    AccountThrottle(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        FailureWindow window = failures.get(key);
        return window != null && window.isBlockedAt(clock.instant());
    }

    /** Registra uma falha e informa se a chave ficou bloqueada. */
    public boolean recordFailure(String key, int maxFailures, Duration blockFor) {
        Instant now = clock.instant();
        FailureWindow updated = failures.compute(key, (ignored, current) -> {
            if (current != null && current.isBlockedAt(now)) {
                return current;
            }
            int count = current == null || current.isExpiredAt(now) ? 1 : current.count() + 1;
            Instant blockedUntil = count >= maxFailures ? now.plus(blockFor) : null;
            return new FailureWindow(count, blockedUntil, now.plus(blockFor));
        });
        return updated.isBlockedAt(now);
    }

    public void block(String key, Duration blockFor) {
        Instant until = clock.instant().plus(blockFor);
        failures.put(key, new FailureWindow(0, until, until));
    }

    public void reset(String key) {
        failures.remove(key);
    }

    /** Aceita a solicitação se respeitar o intervalo mínimo e o limite por hora. */
    public boolean tryAcquire(String key, Duration cooldown, int maxPerHour) {
        Instant now = clock.instant();
        AtomicBoolean allowed = new AtomicBoolean(false);
        requests.compute(key, (ignored, current) -> {
            Deque<Instant> window = current == null ? new ArrayDeque<>() : current;
            discardOlderThan(window, now.minus(REQUEST_WINDOW));
            Instant last = window.peekLast();
            boolean cooledDown = last == null || !last.plus(cooldown).isAfter(now);
            if (cooledDown && window.size() < maxPerHour) {
                window.addLast(now);
                allowed.set(true);
            }
            return window;
        });
        return allowed.get();
    }

    @Scheduled(fixedDelay = PURGE_INTERVAL_MS)
    void purgeExpired() {
        Instant now = clock.instant();
        failures.entrySet().removeIf(entry -> entry.getValue().isExpiredAt(now));
        for (String key : requests.keySet()) {
            requests.computeIfPresent(key, (ignored, window) -> {
                discardOlderThan(window, now.minus(REQUEST_WINDOW));
                return window.isEmpty() ? null : window;
            });
        }
    }

    private static void discardOlderThan(Deque<Instant> window, Instant limit) {
        while (!window.isEmpty() && window.peekFirst().isBefore(limit)) {
            window.pollFirst();
        }
    }

    private record FailureWindow(int count, Instant blockedUntil, Instant expiresAt) {
        boolean isBlockedAt(Instant now) {
            return blockedUntil != null && blockedUntil.isAfter(now);
        }

        boolean isExpiredAt(Instant now) {
            return !expiresAt.isAfter(now) && !isBlockedAt(now);
        }
    }
}
