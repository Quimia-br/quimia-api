package com.api.quimia.domain.safety.internal.usecase;

import com.api.quimia.domain.safety.SafetyGateway;
import com.api.quimia.domain.safety.dto.SubstanciaView;
import org.springframework.stereotype.Service;

@Service
public class ConsultarSubstanciaUseCase {
    private final SafetyGateway seguranca;

    public ConsultarSubstanciaUseCase(SafetyGateway seguranca) {
        this.seguranca = seguranca;
    }

    public SubstanciaView execute(Integer substanciaId) {
        return seguranca.substancia(substanciaId).orElseThrow(() -> new SafetyException("substancia_nao_encontrada", 404));
    }
}
