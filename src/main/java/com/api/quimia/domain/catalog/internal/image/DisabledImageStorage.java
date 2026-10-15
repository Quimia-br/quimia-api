package com.api.quimia.domain.catalog.internal.image;

/** Usado quando as variáveis do Cloudinary não foram configuradas: só falha se uma imagem for enviada. */
final class DisabledImageStorage implements ImageStorage {
    @Override
    public String upload(byte[] conteudo, String pasta) {
        throw new ImageStorageException(ImageStorageException.Motivo.INDISPONIVEL);
    }

    @Override
    public void delete(String url, String pasta) {
        // nada foi enviado por este armazenamento, então não há o que apagar
    }
}
