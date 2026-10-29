package com.api.quimia.domain.partner.internal.usecase;

import com.api.quimia.domain.partner.internal.dto.PontoSalvoView;
import com.api.quimia.domain.partner.internal.persistence.PontoSalvoRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Último ponto salvo (a Home pede `tipo=descarte`). Removido o último, vale o anterior. */
@Service
public class ConsultarUltimoPontoSalvoUseCase {
    private final PontoSalvoRepository salvos;

    public ConsultarUltimoPontoSalvoUseCase(PontoSalvoRepository salvos) {
        this.salvos = salvos;
    }

    @Transactional(readOnly = true)
    public PontoSalvoView execute(UUID usuarioId, String tipo) {
        PontosSalvos.validarTipo(tipo);
        return salvos.doUsuario(usuarioId, tipo, PageRequest.of(0, 1)).getContent().stream()
                .findFirst()
                .map(PontosSalvos::view)
                .orElseThrow(() -> new PartnerException("ponto_salvo_nao_encontrado", 404));
    }
}
