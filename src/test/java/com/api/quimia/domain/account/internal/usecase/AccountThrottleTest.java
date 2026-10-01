package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AccountThrottleTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
    private final AccountThrottle throttle = new AccountThrottle(clock);

    @Test
    void blocksAfterMaxFailuresUntilWindowPasses() {
        for (int i = 0; i < 4; i++) {
            assertThat(throttle.recordFailure("login", 5, Duration.ofMinutes(15))).isFalse();
        }
        assertThat(throttle.recordFailure("login", 5, Duration.ofMinutes(15))).isTrue();
        assertThat(throttle.isBlocked("login")).isTrue();

        clock.advance(Duration.ofMinutes(16));
        assertThat(throttle.isBlocked("login")).isFalse();
        assertThat(throttle.recordFailure("login", 5, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void failuresWhileBlockedDoNotLiftTheBlock() {
        throttle.block("challenge", Duration.ofMinutes(15));

        assertThat(throttle.recordFailure("challenge", 5, Duration.ofMinutes(15))).isTrue();
        assertThat(throttle.isBlocked("challenge")).isTrue();
    }

    @Test
    void resetClearsFailures() {
        throttle.recordFailure("login", 2, Duration.ofMinutes(15));
        throttle.reset("login");

        assertThat(throttle.recordFailure("login", 2, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void requestsRespectCooldownAndHourlyLimit() {
        assertThat(throttle.tryAcquire("recovery", Duration.ofSeconds(15), 2)).isTrue();
        assertThat(throttle.tryAcquire("recovery", Duration.ofSeconds(15), 2)).isFalse();

        clock.advance(Duration.ofSeconds(15));
        assertThat(throttle.tryAcquire("recovery", Duration.ofSeconds(15), 2)).isTrue();
        clock.advance(Duration.ofSeconds(15));
        assertThat(throttle.tryAcquire("recovery", Duration.ofSeconds(15), 2)).isFalse();

        clock.advance(Duration.ofHours(1));
        assertThat(throttle.tryAcquire("recovery", Duration.ofSeconds(15), 2)).isTrue();
    }

    @Test
    void purgeDropsExpiredEntries() {
        throttle.recordFailure("login", 5, Duration.ofMinutes(15));
        throttle.tryAcquire("recovery", Duration.ofSeconds(15), 5);

        clock.advance(Duration.ofHours(2));
        throttle.purgeExpired();

        assertThat(throttle.recordFailure("login", 2, Duration.ofMinutes(15))).isFalse();
        assertThat(throttle.tryAcquire("recovery", Duration.ofSeconds(15), 1)).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
