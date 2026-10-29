package com.api.quimia.domain.partner.internal.usecase;

import com.api.quimia.domain.partner.internal.dto.PontoSalvoView;
import com.api.quimia.domain.partner.internal.model.PontoParceiro;
import com.api.quimia.domain.partner.internal.model.PontoSalvo;

/** Regras comuns às rotas de pontos salvos. */
final class PontosSalvos {
    static final int TAMANHO_MAXIMO = 50;

    private PontosSalvos() {}

    /** Nos pontos salvos o tipo é opcional: ausente traz compra e descarte. */
    static void validarTipo(String tipo) {
        if (tipo != null && !ListarPontosUseCase.TIPOS.contains(tipo)) {
            throw new PartnerException("tipo_invalido", 400);
        }
    }

    /** `par` é uma linha de `PontoSalvoRepository.doUsuario`: (PontoSalvo, PontoParceiro). */
    static PontoSalvoView view(Object[] par) {
        return view((PontoSalvo) par[0], (PontoParceiro) par[1]);
    }

    static PontoSalvoView view(PontoSalvo salvo, PontoParceiro ponto) {
        return new PontoSalvoView(
                ponto.getId(), ponto.getNome(), ponto.getTipo(), ponto.getCep(), ponto.getEstado(), ponto.getBairro(),
                ponto.getRua(), ponto.getNumero(), ponto.getComplemento(), ponto.getLatitude(), ponto.getLongitude(),
                salvo.getCriadoEm());
    }
}
