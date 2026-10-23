package com.api.quimia.domain.assistant.internal.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.domain.assistant.internal.client.AgenteKemi;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiDesligado;
import com.api.quimia.domain.assistant.internal.client.HttpAgenteKemi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgenteKemiConfigurationTest {
    private final ApplicationContextRunner contexto =
            new ApplicationContextRunner().withUserConfiguration(AgenteKemiConfiguration.class);

    @Test
    void semUrlOChatFicaDesligado() {
        contexto.withPropertyValues("quimia.agente.url=  ")
                .run(app -> assertThat(app.getBean(AgenteKemi.class)).isInstanceOf(AgenteKemiDesligado.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://kemi.discloud.app", "http://localhost:8000/", "  HTTPS://kemi.app  "})
    void urlHttpLigaOClienteHttp(String url) {
        contexto.withPropertyValues("quimia.agente.url=" + url)
                .run(app -> assertThat(app.getBean(AgenteKemi.class)).isInstanceOf(HttpAgenteKemi.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"kemi.discloud.app", "ftp://kemi.app", "file:///etc/passwd", "http://", "http:// espaço", "/chat"})
    void urlQueNaoEhHttpAbsolutaImpedeASubida(String url) {
        contexto.withPropertyValues("quimia.agente.url=" + url)
                .run(app -> assertThat(app).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void prazoNaoPositivoImpedeASubida(String prazo) {
        contexto.withPropertyValues("quimia.agente.url=https://kemi.app", "quimia.agente.timeout-segundos=" + prazo)
                .run(app -> assertThat(app).hasFailed());
    }
}
