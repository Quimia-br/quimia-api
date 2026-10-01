package com.api.quimia.infra.security;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final int BCRYPT_STRENGTH = 12;
    private static final String USUARIO_PRINCIPAL = JwtAuthenticationFilter.PRINCIPAL_AUTHORITY_PREFIX + "USUARIO";
    private static final String EMPRESA_PRINCIPAL = JwtAuthenticationFilter.PRINCIPAL_AUTHORITY_PREFIX + "EMPRESA";
    private final JwtAuthenticationFilter jwt;
    private final CookieCsrfFilter csrf;

    public SecurityConfig(JwtAuthenticationFilter jwt, CookieCsrfFilter csrf) {
        this.jwt = jwt;
        this.csrf = csrf;
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.cors(cors -> {});
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/verify-recovery-code",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/mobile/login",
                        "/api/v1/auth/mobile/refresh",
                        "/api/v1/auth/mobile/logout",
                        "/api/v1/auth/firebase",
                        "/api/v1/empresas/auth/register",
                        "/api/v1/empresas/auth/login",
                        "/api/v1/empresas/auth/refresh",
                        "/api/v1/empresas/auth/logout",
                        "/api/v1/empresas/auth/forgot-password",
                        "/api/v1/empresas/auth/reset-password",
                        "/actuator/health")
                .permitAll()
                .requestMatchers("/api/v1/usuarios/**")
                .hasAuthority(USUARIO_PRINCIPAL)
                .requestMatchers("/api/v1/empresas/me/**")
                .hasAuthority(EMPRESA_PRINCIPAL)
                .requestMatchers("/api/v1/admin/**")
                .hasRole("ADMIN")
                .anyRequest()
                .authenticated());
        http.addFilterBefore(csrf, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401}");
                })
                .accessDeniedHandler((request, response, error) -> {
                    response.setStatus(403);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"title\":\"Forbidden\",\"status\":403}");
                }));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
        // Senhas legadas em `usuario.senha`/`empresa.senha` sem prefixo {id}: BCrypt puro é aceito;
        // qualquer outro formato simplesmente não confere (exige recuperação de senha).
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000}") String origins) {
        List<String> allowed = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowed);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Token"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
