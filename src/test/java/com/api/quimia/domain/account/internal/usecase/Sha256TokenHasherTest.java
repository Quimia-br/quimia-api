package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void generatorProducesHashes() {
        VerificationTokenGenerator generator =
                new Sha256TokenHasher.RandomVerificationTokenGenerator();
        String raw = generator.generate();
        assertThat(generator.hash(raw)).hasSize(64);
        assertThatThrownBy(() -> {
            throw new AccountException("invalid_token", 400);
        }).isInstanceOf(AccountException.class);
    }
}
