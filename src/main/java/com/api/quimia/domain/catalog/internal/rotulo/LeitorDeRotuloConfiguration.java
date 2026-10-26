package com.api.quimia.domain.catalog.internal.rotulo;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class LeitorDeRotuloConfiguration {
    private static final Set<String> ESQUEMAS = Set.of("http", "https");

    /**
     * Mesmo agente e mesmo token do chat da Kemi (`quimia.agente.*`), com prazo próprio: quem escaneia espera diante
     * da câmera. Sem URL a busca responde 503 `agente_indisponivel`; configuração que quebraria em uso impede o boot.
     */
    @Bean
    LeitorDeRotulo leitorDeRotulo(
            @Value("${quimia.agente.url:}") String url,
            @Value("${quimia.agente.token:}") String token,
            @Value("${quimia.agente.timeout-rotulo-segundos:30}") long timeoutSegundos) {
        if (url.isBlank()) {
            return new LeitorDeRotuloDesligado();
        }
        if (!urlHttp(url.trim())) {
            throw new IllegalStateException("QUIMIA_AGENTE_URL must be an absolute http(s) URL");
        }
        if (timeoutSegundos <= 0) {
            throw new IllegalStateException("QUIMIA_AGENTE_TIMEOUT_ROTULO_SEGUNDOS must be positive");
        }
        return HttpLeitorDeRotulo.criar(url.trim(), token.trim(), Duration.ofSeconds(timeoutSegundos));
    }

    private static boolean urlHttp(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() != null && ESQUEMAS.contains(uri.getScheme().toLowerCase(Locale.ROOT)) && uri.getHost() != null;
        } catch (IllegalArgumentException invalida) {
            return false;
        }
    }
}
