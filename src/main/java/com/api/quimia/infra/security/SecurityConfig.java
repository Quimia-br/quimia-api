package com.api.quimia.infra.security;

import com.api.quimia.infra.web.RespostaProblema;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
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
        // Sem isto, qualquer exceção não tratada (ex.: coluna inexistente no banco) é reencaminhada
        // para /error, que exigiria token, e o cliente recebe 401 em vez do 500 real.
        http.authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers(
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
                .requestMatchers(
                        "/api/v1/usuarios/**",
                        "/api/v1/produtos/**",
                        "/api/v1/estante/**",
                        "/api/v1/substancias/**",
                        "/api/v1/comodos/**",
                        "/api/v1/misturas/**",
                        "/api/v1/pontos/**",
                        "/api/v1/chat/**")
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
                .authenticationEntryPoint((request, response, error) ->
                        RespostaProblema.escrever(request, response, HttpStatus.UNAUTHORIZED, "nao_autenticado"))
                .accessDeniedHandler((request, response, error) ->
                        RespostaProblema.escrever(request, response, HttpStatus.FORBIDDEN, "acesso_negado")));
        return http.build();
    }

    /** Caminhos recusados pelo StrictHttpFirewall (`;`, `//`, `%2e`...) também respondem no formato do contrato. */
    @Bean
    RequestRejectedHandler requisicaoRejeitada() {
        return (request, response, error) -> {
            log.debug("requisição recusada pelo firewall: {}", error.getMessage());
            RespostaProblema.escrever(request, response, HttpStatus.BAD_REQUEST, "requisicao_rejeitada");
        };
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
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Token"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
