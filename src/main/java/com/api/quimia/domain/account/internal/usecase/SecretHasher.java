package com.api.quimia.domain.account.internal.usecase;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** HMAC-SHA-256 com segredo externo para códigos e impressões de credencial. */
@Component
public class SecretHasher {
    private static final int MIN_SECRET_BYTES = 32;
    private static final String ALGORITHM = "HmacSHA256";
    private static final String PART_SEPARATOR = "\u001f";

    private final SecretKeySpec key;

    public SecretHasher(@Value("${app.auth.hmac-secret:}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("APP_AUTH_HMAC_SECRET must contain at least 32 bytes");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hmac(String... parts) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] value = mac.doFinal(String.join(PART_SEPARATOR, parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Could not compute HMAC", error);
        }
    }

    public boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }
}
