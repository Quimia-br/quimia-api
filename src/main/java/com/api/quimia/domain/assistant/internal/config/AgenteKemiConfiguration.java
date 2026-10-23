package com.api.quimia.domain.assistant.internal.config;

import com.api.quimia.domain.assistant.internal.client.AgenteKemi;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiDesligado;
import com.api.quimia.domain.assistant.internal.client.HttpAgenteKemi;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AgenteKemiConfiguration {
    private static final Set<String> ESQUEMAS = Set.of("http", "https");

    /**
     * Sem URL o chat fica desligado (503 agente_indisponivel). A API não sobe com configuração que quebraria em uso:
     * URL que não é http(s) absoluta ou prazo não positivo.
     */
    @Bean
    AgenteKemi agenteKemi(
            @Value("${quimia.agente.url:}") String url,
            @Value("${quimia.agente.token:}") String token,
            @Value("${quimia.agente.timeout-segundos:120}") long timeoutSegundos) {
        if (url.isBlank()) {
            return new AgenteKemiDesligado();
        }
        if (!urlHttp(url.trim())) {
            throw new IllegalStateException("QUIMIA_AGENTE_URL must be an absolute http(s) URL");
        }
        if (timeoutSegundos <= 0) {
            throw new IllegalStateException("QUIMIA_AGENTE_TIMEOUT_SEGUNDOS must be positive");
        }
        return HttpAgenteKemi.criar(url.trim(), token.trim(), Duration.ofSeconds(timeoutSegundos));
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
