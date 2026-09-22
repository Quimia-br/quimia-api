package com.api.quimia;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

public final class TestJwtKeys implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final KeyPaths KEYS = createKeys();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testJwtKeys", Map.of(
                "quimia.jwt.private-key-path", KEYS.privateKey().toString(),
                "quimia.jwt.public-key-path", KEYS.publicKey().toString())));
    }

    private static KeyPaths createKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Path privateKey = Files.createTempFile("quimia-test-private-", ".pem");
            Path publicKey = Files.createTempFile("quimia-test-public-", ".pem");
            privateKey.toFile().deleteOnExit();
            publicKey.toFile().deleteOnExit();
            Files.writeString(privateKey, pem("PRIVATE KEY", pair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
            Files.writeString(publicKey, pem("PUBLIC KEY", pair.getPublic().getEncoded()), StandardCharsets.US_ASCII);
            return new KeyPaths(privateKey, publicKey);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create test JWT keys", e);
        }
    }

    private static String pem(String label, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    private record KeyPaths(Path privateKey, Path publicKey) {}
}
