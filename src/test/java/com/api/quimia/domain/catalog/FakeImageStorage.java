package com.api.quimia.domain.catalog;

import com.api.quimia.domain.catalog.internal.image.ImageStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorageException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Substitui o Cloudinary nos testes web e registra o que foi enviado e apagado. */
public final class FakeImageStorage implements ImageStorage {
    public static final String PREFIXO = "https://fake.img/produtos/";
    private static final int TAMANHO_ACIMA_DO_LIMITE_DA_COLUNA = 500;

    private final AtomicInteger sequencia = new AtomicInteger();
    private final List<String> enviadas = new CopyOnWriteArrayList<>();
    private final List<String> apagadas = new CopyOnWriteArrayList<>();
    private volatile boolean urlLonga;
    private volatile ImageStorageException.Motivo falha;

    @Override
    public String upload(byte[] conteudo) {
        if (falha != null) {
            throw new ImageStorageException(falha);
        }
        String url = PREFIXO + sequencia.incrementAndGet() + ".png";
        if (urlLonga) {
            url = url + "x".repeat(TAMANHO_ACIMA_DO_LIMITE_DA_COLUNA);
        }
        enviadas.add(url);
        return url;
    }

    @Override
    public void delete(String url) {
        if (url != null) {
            apagadas.add(url);
        }
    }

    /** A URL passa a estourar `foto_url`, forçando falha no INSERT depois do upload. */
    public void gerarUrlLonga() {
        urlLonga = true;
    }

    /** O próximo upload falha como se o Cloudinary estivesse fora do ar ou sem credenciais. */
    public void falharComo(ImageStorageException.Motivo motivo) {
        falha = motivo;
    }

    public List<String> enviadas() {
        return List.copyOf(enviadas);
    }

    public List<String> apagadas() {
        return List.copyOf(apagadas);
    }

    public void limpar() {
        enviadas.clear();
        apagadas.clear();
        urlLonga = false;
        falha = null;
    }
}
