package com.api.quimia.domain.catalog.internal.image;

/** Usado quando as variáveis do Cloudinary não foram configuradas: só falha se uma imagem ou um PDF for enviado. */
final class DisabledImageStorage implements ArmazenamentoDeArquivos {
    @Override
    public String upload(byte[] conteudo, String pasta) {
        throw new ImageStorageException(ImageStorageException.Motivo.INDISPONIVEL);
    }

    @Override
    public void delete(String url, String pasta) {
        // nada foi enviado por este armazenamento, então não há o que apagar
    }

    @Override
    public String enviarPdf(byte[] conteudo, String pasta, String nomeArquivo) {
        throw new ImageStorageException(ImageStorageException.Motivo.INDISPONIVEL);
    }

    @Override
    public void apagarPdf(String url, String pasta) {
        // nada foi enviado por este armazenamento, então não há o que apagar
    }
}
