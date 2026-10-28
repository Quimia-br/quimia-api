package com.api.quimia.domain.catalog.internal.rotulo;

import java.util.List;

/** Reconhecimento do rótulo na foto, feito pelo quimia-agent. */
public interface LeitorDeRotulo {
    /** Produtos que o agente leu na imagem (JPEG, PNG ou WebP já validada); lista vazia quando não achou nenhum. */
    List<CandidatoDoRotulo> identificar(byte[] imagem);
}
