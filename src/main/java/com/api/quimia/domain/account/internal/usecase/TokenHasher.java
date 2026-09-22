package com.api.quimia.domain.account.internal.usecase;

public interface TokenHasher {
    String hash(String raw);

    boolean matches(String raw, String hash);
}
