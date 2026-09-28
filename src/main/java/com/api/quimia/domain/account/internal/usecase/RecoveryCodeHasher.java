package com.api.quimia.domain.account.internal.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecoveryCodeHasher {
    private final byte[] pepper;

    public RecoveryCodeHasher(@Value("${app.auth.recovery-code-pepper:}") String pepper) {
        if (pepper == null || pepper.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("APP_AUTH_RECOVERY_CODE_PEPPER must contain at least 32 bytes");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(UUID challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            byte[] value = mac.doFinal((challengeId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (Exception error) {
            throw new IllegalStateException("Could not hash recovery code", error);
        }
    }

    public boolean matches(UUID challengeId, String code, String expectedHash) {
        return MessageDigest.isEqual(
                hash(challengeId, code).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
