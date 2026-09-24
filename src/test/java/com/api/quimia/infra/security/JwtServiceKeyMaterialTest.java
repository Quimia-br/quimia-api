package com.api.quimia.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceKeyMaterialTest {
    @Test
    void acceptsMatchingBase64DerKeysAndRoundTripsJwt() throws Exception {
        KeyPair pair = newRsaPair();
        JwtService service = newService(encoded(pair.getPrivate().getEncoded()),
                encoded(pair.getPublic().getEncoded()), "", "");
        UUID subject = UUID.randomUUID();

        assertThat(service.verify(service.issue(subject, "USUARIO")).getSubject())
                .isEqualTo(subject.toString());
    }

    @Test
    void rejectsOnlyOneBase64Key() throws Exception {
        KeyPair pair = newRsaPair();

        assertThatThrownBy(() -> newService(encoded(pair.getPrivate().getEncoded()), "", "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedBase64Key() {
        assertThatThrownBy(() -> newService("%%%", "%%%", "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMismatchedPrivateAndPublicKeys() throws Exception {
        KeyPair privatePair = newRsaPair();
        KeyPair publicPair = newRsaPair();

        assertThatThrownBy(() -> newService(encoded(privatePair.getPrivate().getEncoded()),
                encoded(publicPair.getPublic().getEncoded()), "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingBase64AndPemPaths() {
        assertThatThrownBy(() -> newService("", "", "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    private static KeyPair newRsaPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String encoded(byte[] der) {
        return Base64.getEncoder().encodeToString(der);
    }

    private static JwtService newService(String privateBase64, String publicBase64,
            String privatePath, String publicPath) {
        return new JwtService(privateBase64, publicBase64, privatePath, publicPath,
                "test-kid", "quimia-auth", "quimia-api", 15);
    }
}
