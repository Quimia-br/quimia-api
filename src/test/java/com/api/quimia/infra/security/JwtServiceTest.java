package com.api.quimia.infra.security;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.account.internal.usecase.SessionIssuer;
import com.api.quimia.domain.account.internal.usecase.SignedTokenCodec;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
        classes = {com.api.quimia.Application.class, com.api.quimia.domain.account.AccountTestConfig.class})
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class JwtServiceTest {
    @Autowired
    private JwtService jwt;

    @Autowired
    private SignedTokenCodec codec;

    @Test
    void codecRoundTripsClaimsAndRejectsOtherTokenUses() {
        String token = codec.sign("refresh", "subject-1", Map.of("tipo", "usuario"), Instant.now().plusSeconds(120));

        var verified = codec.verify(token, "refresh").orElseThrow();
        assertThat(verified.subject()).isEqualTo("subject-1");
        assertThat(verified.text("tipo")).isEqualTo("usuario");
        assertThat(codec.verify(token, SessionIssuer.ACCESS_TOKEN)).isEmpty();
        assertThat(codec.verify(token + "x", "refresh")).isEmpty();
        assertThat(codec.verify(null, "refresh")).isEmpty();
    }

    @Test
    void issueAndVerify() {
        UUID subject = UUID.randomUUID();
        String token = accessToken(subject);
        assertThat(jwt.verify(token, SessionIssuer.ACCESS_TOKEN).getSubject()).isEqualTo(subject.toString());
    }

    @Test
    void tamperedFails() {
        String token = accessToken(UUID.randomUUID()) + "x";
        assertThatThrownBy(() -> jwt.verify(token)).isInstanceOf(JwtService.InvalidTokenException.class);
    }

    private String accessToken(UUID subject) {
        return jwt.sign(
                SessionIssuer.ACCESS_TOKEN,
                subject.toString(),
                Map.of("tipo", "usuario", "role", "USUARIO"),
                Instant.now().plusSeconds(120));
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
