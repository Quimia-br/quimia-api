package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Sha256TokenHasherTest {
    private final TokenHasher hasher = new Sha256TokenHasher();

    @Test
    void hashesAndMatches() {
        String hash = hasher.hash("token-123");
        assertThat(hash).hasSize(64);
        assertThat(hasher.matches("token-123", hash)).isTrue();
        assertThat(hasher.matches("other", hash)).isFalse();
    }

}
