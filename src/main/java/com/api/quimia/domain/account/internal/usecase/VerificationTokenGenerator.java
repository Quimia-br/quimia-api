package com.api.quimia.domain.account.internal.usecase;

public interface VerificationTokenGenerator {
    String generate();

    String hash(String raw);
}
