package com.api.quimia.domain.account.internal.usecase;

import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class Sha256TokenHasher implements TokenHasher {
    @Override
    public String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean matches(String raw, String hash) {
        return hash(raw).equals(hash);
    }
}
