package com.api.quimia.domain.catalog.internal.rotulo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LeitorDeRotuloConfigurationTest {
    private final ApplicationContextRunner contexto =
            new ApplicationContextRunner().withUserConfiguration(LeitorDeRotuloConfiguration.class);

    @Test
    void semUrlABuscaPorRotuloFicaDesligada() {
        contexto.withPropertyValues("quimia.agente.url=  ")
                .run(app -> assertThat(app.getBean(LeitorDeRotulo.class)).isInstanceOf(LeitorDeRotuloDesligado.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://kemi.discloud.app", "http://localhost:8000/", "  HTTPS://kemi.app  "})
    void urlHttpLigaOClienteHttp(String url) {
        contexto.withPropertyValues("quimia.agente.url=" + url)
                .run(app -> assertThat(app.getBean(LeitorDeRotulo.class)).isInstanceOf(HttpLeitorDeRotulo.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"kemi.discloud.app", "ftp://kemi.app", "file:///etc/passwd", "http://", "/chat"})
    void urlQueNaoEhHttpAbsolutaImpedeASubida(String url) {
        contexto.withPropertyValues("quimia.agente.url=" + url).run(app -> assertThat(app).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void prazoNaoPositivoImpedeASubida(String prazo) {
        contexto.withPropertyValues("quimia.agente.url=https://kemi.app", "quimia.agente.timeout-rotulo-segundos=" + prazo)
                .run(app -> assertThat(app).hasFailed());
    }
}
