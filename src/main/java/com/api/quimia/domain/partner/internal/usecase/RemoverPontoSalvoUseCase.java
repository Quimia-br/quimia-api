package com.api.quimia.domain.partner.internal.usecase;

import com.api.quimia.domain.partner.internal.persistence.PontoSalvoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tira o ponto dos salvos do usuário. Idempotente: ponto que não estava salvo não é erro. */
@Service
public class RemoverPontoSalvoUseCase {
    private final PontoSalvoRepository salvos;

    public RemoverPontoSalvoUseCase(PontoSalvoRepository salvos) {
        this.salvos = salvos;
    }

    @Transactional
    public void execute(UUID usuarioId, Integer pontoId) {
        salvos.remover(usuarioId, pontoId);
    }
}
