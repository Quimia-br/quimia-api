package com.api.quimia.domain.catalog;

import com.api.quimia.domain.catalog.internal.rotulo.CandidatoDoRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotuloException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Substitui o agente nos testes web: devolve o que o teste programar e guarda as imagens recebidas. */
public final class FakeLeitorDeRotulo implements LeitorDeRotulo {
    private final List<byte[]> recebidas = new CopyOnWriteArrayList<>();
    private volatile List<CandidatoDoRotulo> candidatos = List.of();
    private volatile LeitorDeRotuloException.Motivo falha;

    @Override
    public List<CandidatoDoRotulo> identificar(byte[] imagem) {
        recebidas.add(imagem);
        if (falha != null) {
            throw new LeitorDeRotuloException(falha);
        }
        return candidatos;
    }

    public void ler(CandidatoDoRotulo... lidos) {
        candidatos = List.of(lidos);
    }

    public void falharComo(LeitorDeRotuloException.Motivo motivo) {
        falha = motivo;
    }

    public int chamadas() {
        return recebidas.size();
    }

    public void limpar() {
        recebidas.clear();
        candidatos = List.of();
        falha = null;
    }
}
