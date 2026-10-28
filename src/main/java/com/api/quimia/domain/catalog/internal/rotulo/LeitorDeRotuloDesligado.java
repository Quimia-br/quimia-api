package com.api.quimia.domain.catalog.internal.rotulo;

import java.util.List;

/** Sem `QUIMIA_AGENTE_URL`: a busca por foto do rótulo responde 503 `agente_indisponivel`. */
public final class LeitorDeRotuloDesligado implements LeitorDeRotulo {
    @Override
    public List<CandidatoDoRotulo> identificar(byte[] imagem) {
        throw new LeitorDeRotuloException(LeitorDeRotuloException.Motivo.INDISPONIVEL);
    }
}
