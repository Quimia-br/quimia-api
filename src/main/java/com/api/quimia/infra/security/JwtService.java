package com.api.quimia.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String kid;
    private final String issuer;
    private final String audience;
    private final long accessTtlMinutes;

    public JwtService(
            @Value("${quimia.jwt.private-key-path:}") String privateKeyPath,
            @Value("${quimia.jwt.public-key-path:}") String publicKeyPath,
            @Value("${quimia.jwt.kid:local-dev-01}") String kid,
            @Value("${quimia.jwt.issuer:quimia-auth}") String issuer,
            @Value("${quimia.jwt.audience:quimia-api}") String audience,
            @Value("${quimia.jwt.access-ttl-minutes:15}") long accessTtlMinutes) {
        try {
            this.privateKey = readPrivate(privateKeyPath);
            this.publicKey = readPublic(publicKeyPath);
        } catch (Exception e) {
            throw new IllegalStateException("jwt keys unavailable", e);
        }
        this.kid = kid;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public String issue(UUID subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(kid).and()
                .subject(subject.toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlMinutes * 60)))
                .signWith(privateKey)
                .compact();
    }

    public Claims verify(String token) {
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .keyLocator(header -> {
                        Object headerKid = header.get("kid");
                        if (headerKid == null || !kid.equals(headerKid.toString())) {
                            throw new JwtException("unknown kid");
                        }
                        return publicKey;
                    })
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token);
            return parsed.getPayload();
        } catch (JwtException e) {
            throw new InvalidTokenException();
        }
    }

    private static PrivateKey readPrivate(String path) throws Exception {
        String pem = Files.readString(Path.of(path));
        byte[] der = Base64.getMimeDecoder().decode(pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", ""));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey readPublic(String path) throws Exception {
        String pem = Files.readString(Path.of(path));
        byte[] der = Base64.getMimeDecoder().decode(pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", ""));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    public static class InvalidTokenException extends RuntimeException {}
}
