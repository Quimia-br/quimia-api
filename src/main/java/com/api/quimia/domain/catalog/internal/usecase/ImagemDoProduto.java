package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.internal.image.ImageStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorageException;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Valida a imagem enviada no formulário e a leva ao armazenamento externo sem deixar lixo: se a
 * transação do cadastro/alteração for desfeita, a imagem nova é apagada; se der certo, a antiga é.
 */
@Component
public class ImagemDoProduto {
    static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    private static final int ASSINATURA_MINIMA = 12;

    private final ImageStorage storage;

    public ImagemDoProduto(ImageStorage storage) {
        this.storage = storage;
    }

    /** Bytes da imagem validada, ou `null` quando o formulário não trouxe arquivo. */
    public byte[] ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return null;
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new CatalogException("imagem_muito_grande", 413);
        }
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        }
        if (!formatoSuportado(conteudo)) {
            throw new CatalogException("imagem_invalida", 415);
        }
        return conteudo;
    }

    /**
     * Envia a imagem e registra a compensação na transação corrente. `urlAnterior` (pode ser nula)
     * é apagada só depois do commit.
     */
    public String enviar(byte[] conteudo, String urlAnterior) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("upload de imagem exige transação ativa");
        }
        String url;
        try {
            url = storage.upload(conteudo);
        } catch (ImageStorageException falha) {
            throw switch (falha.motivo()) {
                case INDISPONIVEL -> new CatalogException("armazenamento_imagem_indisponivel", 503);
                case FALHOU -> new CatalogException("armazenamento_imagem_falhou", 502);
            };
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                storage.delete(status == STATUS_COMMITTED ? urlAnterior : url);
            }
        });
        return url;
    }

    /** JPEG, PNG e WebP pelos bytes iniciais, ignorando o `Content-Type` e o nome informados. */
    static boolean formatoSuportado(byte[] b) {
        if (b.length < ASSINATURA_MINIMA) {
            return false;
        }
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
        boolean webp = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpeg || png || webp;
    }
}
