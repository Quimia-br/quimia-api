package com.api.quimia.infra.security;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.account.internal.usecase.AccessTokenIssuer;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
        classes = {com.api.quimia.Application.class, com.api.quimia.domain.account.AccountTestConfig.class},
        properties = "quimia.jwt.access-ttl-minutes=2")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class JwtServiceTest {
    @Autowired
    private JwtService jwt;

    @Autowired
    private AccessTokenIssuer accessTokens;

    @Test
    void responseExpiryUsesConfiguredJwtTtl() {
        var issued = accessTokens.issue(UUID.randomUUID().toString(), "USUARIO");
        assertThat(issued.expiresInSeconds()).isEqualTo(120);
    }

    @Test
    void issueAndVerify() {
        UUID subject = UUID.randomUUID();
        String token = jwt.issue(subject, "USUARIO");
        assertThat(jwt.verify(token).getSubject()).isEqualTo(subject.toString());
    }

    @Test
    void tamperedFails() {
        UUID subject = UUID.randomUUID();
        String token = jwt.issue(subject, "USUARIO") + "x";
        assertThatThrownBy(() -> jwt.verify(token)).isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void expiredFails() {
        PrivateKey privateKey = (PrivateKey) ReflectionTestUtils.getField(jwt, "privateKey");
        String kid = (String) ReflectionTestUtils.getField(jwt, "kid");
        Instant now = Instant.now();
        String token = Jwts.builder()
                .header()
                .keyId(kid)
                .and()
                .subject(UUID.randomUUID().toString())
                .issuer("quimia-auth")
                .audience()
                .add("quimia-api")
                .and()
                .claim("role", "USUARIO")
                .issuedAt(Date.from(now.minusSeconds(1800)))
                .expiration(Date.from(now.minusSeconds(900)))
                .signWith(privateKey)
                .compact();
        assertThatThrownBy(() -> jwt.verify(token)).isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void wrongIssuerFails() {
        PrivateKey privateKey = (PrivateKey) ReflectionTestUtils.getField(jwt, "privateKey");
        String kid = (String) ReflectionTestUtils.getField(jwt, "kid");
        Instant now = Instant.now();
        String token = Jwts.builder()
                .header()
                .keyId(kid)
                .and()
                .subject(UUID.randomUUID().toString())
                .issuer("other-issuer")
                .audience()
                .add("quimia-api")
                .and()
                .claim("role", "USUARIO")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(privateKey)
                .compact();
        assertThatThrownBy(() -> jwt.verify(token)).isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void wrongAudienceFails() {
        PrivateKey privateKey = (PrivateKey) ReflectionTestUtils.getField(jwt, "privateKey");
        String kid = (String) ReflectionTestUtils.getField(jwt, "kid");
        Instant now = Instant.now();
        String token = Jwts.builder()
                .header()
                .keyId(kid)
                .and()
                .subject(UUID.randomUUID().toString())
                .issuer("quimia-auth")
                .audience()
                .add("other-api")
                .and()
                .claim("role", "USUARIO")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(privateKey)
                .compact();
        assertThatThrownBy(() -> jwt.verify(token)).isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void unknownKidFails() {
        PrivateKey privateKey = (PrivateKey) ReflectionTestUtils.getField(jwt, "privateKey");
        Instant now = Instant.now();
        String token = Jwts.builder()
                .header()
                .keyId("unknown-kid")
                .and()
                .subject(UUID.randomUUID().toString())
                .issuer("quimia-auth")
                .audience()
                .add("quimia-api")
                .and()
                .claim("role", "USUARIO")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(privateKey)
                .compact();
        assertThatThrownBy(() -> jwt.verify(token)).isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void missingKidFails() {
        PrivateKey privateKey = (PrivateKey) ReflectionTestUtils.getField(jwt, "privateKey");
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("quimia-auth")
                .audience()
                .add("quimia-api")
                .and()
                .claim("role", "USUARIO")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(privateKey)
                .compact();
        assertThatThrownBy(() -> jwt.verify(token)).isInstanceOf(JwtService.InvalidTokenException.class);
    }
}
