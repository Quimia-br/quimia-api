package com.api.quimia.domain.insights.internal.usecase;

import com.api.quimia.domain.insights.internal.dto.ResumoEmpresaView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarResumoUseCase {
    private final ResumoDaEmpresa resumo;

    public ConsultarResumoUseCase(ResumoDaEmpresa resumo) {
        this.resumo = resumo;
    }

    @Transactional(readOnly = true)
    public ResumoEmpresaView execute(Integer empresaId) {
        return resumo.carregar(empresaId);
    }
}
