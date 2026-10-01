package com.api.quimia.infra.security;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.account.internal.usecase.SessionIssuer;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /** Autoridade que separa usuário consumidor de empresa, independentemente do papel. */
    public static final String PRINCIPAL_AUTHORITY_PREFIX = "PRINCIPAL_";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwt;

    public JwtAuthenticationFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                SecurityContextHolder.getContext()
                        .setAuthentication(authenticate(header.substring(BEARER_PREFIX.length())));
            } catch (JwtService.InvalidTokenException invalid) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken authenticate(String token) {
        Claims claims = jwt.verify(token, SessionIssuer.ACCESS_TOKEN);
        String typeClaim = claims.get(SessionIssuer.CLAIM_TYPE, String.class);
        String role = claims.get(SessionIssuer.CLAIM_ROLE, String.class);
        if (typeClaim == null || role == null || claims.getSubject() == null) {
            throw new JwtService.InvalidTokenException();
        }
        AccountPrincipal.Type type;
        try {
            type = AccountPrincipal.Type.fromClaim(typeClaim);
        } catch (IllegalArgumentException unknownType) {
            throw new JwtService.InvalidTokenException();
        }
        return new UsernamePasswordAuthenticationToken(
                new AccountPrincipal(type, claims.getSubject()),
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role),
                        new SimpleGrantedAuthority(PRINCIPAL_AUTHORITY_PREFIX + type.name())));
    }
}
