package com.api.quimia.domain.catalog.internal.image;

/** Armazenamento externo dos PDFs das FDS enviados pelo portal, para o download no detalhe do produto. */
public interface DocumentoStorage {
    /**
     * Envia o PDF para `pasta` (definida pela API, ex.: `fds/12`) com o `nomeArquivo` já higienizado, que aparece no
     * fim da URL pública devolvida.
     */
    String enviarPdf(byte[] conteudo, String pasta, String nomeArquivo);

    /** Apaga um PDF deste armazenamento que esteja em `pasta`; URLs de outra pasta, de outra conta ou nulas são ignoradas. */
    void apagarPdf(String url, String pasta);
}
