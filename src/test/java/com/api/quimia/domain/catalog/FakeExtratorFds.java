package com.api.quimia.domain.catalog;

import com.api.quimia.domain.catalog.internal.fds.ExtratorFds;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFdsException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Substitui o `quimia-extractor-ai` nos testes web. Por padrão devolve a resposta de exemplo do próprio extrator
 * (`src/test/resources/fds/resposta-extrator.json`, copiada de `tests/fixtures/` do repositório dele).
 */
public final class FakeExtratorFds implements ExtratorFds {
    public static final String RESPOSTA_DE_EXEMPLO = lerExemplo();

    private final AtomicInteger chamadas = new AtomicInteger();
    private volatile String resposta = RESPOSTA_DE_EXEMPLO;
    private volatile ExtratorFdsException.Motivo falha;

    @Override
    public String extrair(byte[] pdf) {
        chamadas.incrementAndGet();
        if (falha != null) {
            throw new ExtratorFdsException(falha);
        }
        return resposta;
    }

    public void responder(String json) {
        resposta = json;
    }

    public void falharComo(ExtratorFdsException.Motivo motivo) {
        falha = motivo;
    }

    public int chamadas() {
        return chamadas.get();
    }

    public void limpar() {
        chamadas.set(0);
        resposta = RESPOSTA_DE_EXEMPLO;
        falha = null;
    }

    private static String lerExemplo() {
        try (InputStream exemplo = FakeExtratorFds.class.getResourceAsStream("/fds/resposta-extrator.json")) {
            if (exemplo == null) {
                throw new IllegalStateException("fixture fds/resposta-extrator.json ausente");
            }
            return new String(exemplo.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        }
    }
}
