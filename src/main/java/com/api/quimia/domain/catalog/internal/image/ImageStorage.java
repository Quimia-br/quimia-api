package com.api.quimia.domain.catalog.internal.image;

/** Armazenamento externo das imagens de produtos e dos logos das empresas, separados por pasta. */
public interface ImageStorage {
    /** Envia a imagem para `pasta` (definida pela API, ex.: `produtos`, `empresas/12`) e devolve a URL pública. */
    String upload(byte[] conteudo, String pasta);

    /**
     * Apaga uma imagem deste armazenamento que esteja em `pasta`. URLs de outra pasta, de outra conta ou nulas são
     * ignoradas: a URL pode ter vindo do cliente (ex.: `PATCH /empresas/me`) e não autoriza apagar imagem alheia.
     */
    void delete(String url, String pasta);
}
