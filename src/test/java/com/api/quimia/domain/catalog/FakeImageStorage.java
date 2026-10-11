package com.api.quimia.domain.catalog;

import com.api.quimia.domain.catalog.internal.image.ImageStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorageException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Substitui o Cloudinary nos testes web e registra o que foi enviado e apagado. Como o real, só apaga URL que
 * esteja na pasta informada.
 */
public final class FakeImageStorage implements ImageStorage {
    public static final String BASE = "https://fake.img/";
    public static final String PREFIXO = BASE + "produtos/";
    private static final int TAMANHO_ACIMA_DO_LIMITE_DA_COLUNA = 500;

    private final AtomicInteger sequencia = new AtomicInteger();
    private final List<String> enviadas = new CopyOnWriteArrayList<>();
    private final List<String> apagadas = new CopyOnWriteArrayList<>();
    private volatile boolean urlLonga;
    private volatile ImageStorageException.Motivo falha;
    private volatile Runnable duranteOUpload = () -> {};

    @Override
    public String upload(byte[] conteudo, String pasta) {
        if (falha != null) {
            throw new ImageStorageException(falha);
        }
        duranteOUpload.run();
        String url = BASE + pasta + "/" + sequencia.incrementAndGet() + ".png";
        if (urlLonga) {
            url = url + "x".repeat(TAMANHO_ACIMA_DO_LIMITE_DA_COLUNA);
        }
        enviadas.add(url);
        return url;
    }

    @Override
    public void delete(String url, String pasta) {
        if (url != null && url.startsWith(BASE + pasta + "/")) {
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

    /** Executa `acao` enquanto o "upload" acontece, para simular outra transação nesse intervalo. */
    public void duranteOUpload(Runnable acao) {
        duranteOUpload = acao;
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
        duranteOUpload = () -> {};
    }
}
