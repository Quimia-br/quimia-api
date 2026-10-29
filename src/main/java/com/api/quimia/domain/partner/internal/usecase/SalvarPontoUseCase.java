package com.api.quimia.domain.partner.internal.usecase;

import com.api.quimia.domain.partner.internal.dto.PontoSalvoView;
import com.api.quimia.domain.partner.internal.model.PontoParceiro;
import com.api.quimia.domain.partner.internal.model.PontoSalvo;
import com.api.quimia.domain.partner.internal.persistence.PontoParceiroRepository;
import com.api.quimia.domain.partner.internal.persistence.PontoSalvoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Salva um ponto ativo para o usuário. Idempotente: salvar de novo mantém a data original. */
@Service
public class SalvarPontoUseCase {
    private final PontoParceiroRepository pontos;
    private final PontoSalvoRepository salvos;

    public SalvarPontoUseCase(PontoParceiroRepository pontos, PontoSalvoRepository salvos) {
        this.pontos = pontos;
        this.salvos = salvos;
    }

    @Transactional
    public PontoSalvoView execute(UUID usuarioId, Integer pontoId) {
        PontoParceiro ponto = pontos.findById(pontoId)
                .filter(PontoParceiro::isAtivo)
                .orElseThrow(() -> new PartnerException("ponto_nao_encontrado", 404));
        salvos.salvar(usuarioId, pontoId);
        PontoSalvo salvo = salvos.findByUsuarioIdAndPontoId(usuarioId, pontoId)
                .orElseThrow(() -> new IllegalStateException("ponto salvo sumiu na mesma transação"));
        return PontosSalvos.view(salvo, ponto);
    }
}
