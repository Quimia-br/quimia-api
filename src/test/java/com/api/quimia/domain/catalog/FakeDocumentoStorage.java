package com.api.quimia.domain.catalog;

import com.api.quimia.domain.catalog.internal.image.DocumentoStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorageException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Substitui o Cloudinary para os PDFs das FDS nos testes web. Como o real, só apaga o que está na pasta pedida. */
public final class FakeDocumentoStorage implements DocumentoStorage {
    public static final String BASE = "https://fake.docs/";

    private final List<String> enviados = new CopyOnWriteArrayList<>();
    private final List<String> apagados = new CopyOnWriteArrayList<>();
    private volatile ImageStorageException.Motivo falha;
    private volatile RuntimeException explosao;

    @Override
    public String enviarPdf(byte[] conteudo, String pasta, String nomeArquivo) {
        if (explosao != null) {
            throw explosao;
        }
        if (falha != null) {
            throw new ImageStorageException(falha);
        }
        String url = BASE + pasta + "/" + UUID.randomUUID() + "/" + nomeArquivo;
        enviados.add(url);
        return url;
    }

    @Override
    public void apagarPdf(String url, String pasta) {
        if (url != null && url.startsWith(BASE + pasta + "/")) {
            apagados.add(url);
        }
    }

    public void falharComo(ImageStorageException.Motivo motivo) {
        falha = motivo;
    }

    /** Falha que o contrato do armazenamento não prevê (o cliente do Cloudinary lançando algo inesperado). */
    public void explodirCom(RuntimeException inesperada) {
        explosao = inesperada;
    }

    public List<String> enviados() {
        return List.copyOf(enviados);
    }

    public List<String> apagados() {
        return List.copyOf(apagados);
    }

    public void limpar() {
        enviados.clear();
        apagados.clear();
        falha = null;
        explosao = null;
    }
}
