package com.api.quimia.domain.catalog.internal.image;

/** Imagens e PDFs ficam no mesmo armazenamento (mesma conta Cloudinary e mesmas variáveis). */
interface ArmazenamentoDeArquivos extends ImageStorage, DocumentoStorage {}
