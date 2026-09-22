package com.api.quimia.infra.web;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.infra.security.JwtService;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(classes = {com.api.quimia.Application.class, com.api.quimia.domain.account.AccountTestConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class SecurityNegativeTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    @Test
    void invalidExpiredIssuerAudienceAndKidReturn401() throws Exception {
        assertUnauthorized("invalid");
        assertUnauthorized(signedToken("quimia-auth", "quimia-api", configuredKid(), -1, true) + "x");
        assertUnauthorized(signedToken("quimia-auth", "quimia-api", configuredKid(), -1, true));
        assertUnauthorized(signedToken("other-issuer", "quimia-api", configuredKid(), 900, true));
        assertUnauthorized(signedToken("quimia-auth", "other-api", configuredKid(), 900, true));
        assertUnauthorized(signedToken("quimia-auth", "quimia-api", "unknown-kid", 900, true));
        assertUnauthorized(signedToken("quimia-auth", "quimia-api", null, 900, false));

        assertThat(jwt.verify(jwt.issue(UUID.randomUUID(), "USUARIO")).getSubject()).isNotBlank();
    }

    @Test
    void regularUserCannotReachAdminWriteRoute() throws Exception {
        String token = jwt.issue(UUID.randomUUID(), "USUARIO");
        mvc.perform(patch("/api/v1/admin/usuarios/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String configuredKid() {
        return (String) ReflectionTestUtils.getField(jwt, "kid");
    }

    private String signedToken(String issuer, String audience, String kid, long expiresInSeconds, boolean withKid) {
        PrivateKey privateKey = (PrivateKey) ReflectionTestUtils.getField(jwt, "privateKey");
        Instant now = Instant.now();
        var builder = Jwts.builder();
        if (withKid) {
            builder.header().keyId(kid).and();
        }
        return builder
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .claim("role", "USUARIO")
                .issuedAt(Date.from(now.minusSeconds(expiresInSeconds < 0 ? 600 : 0)))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .signWith(privateKey)
                .compact();
    }
}
