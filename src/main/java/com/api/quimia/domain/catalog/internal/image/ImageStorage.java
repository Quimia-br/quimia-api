package com.api.quimia.domain.catalog.internal.image;

/** Armazenamento externo das imagens dos produtos. */
public interface ImageStorage {
    /** Envia a imagem e devolve a URL pública. */
    String upload(byte[] conteudo);

    /** Apaga uma imagem enviada por este armazenamento; URLs que não são dele (ou nulas) são ignoradas. */
    void delete(String url);
}
