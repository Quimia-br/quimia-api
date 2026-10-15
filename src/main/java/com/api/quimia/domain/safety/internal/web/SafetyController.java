package com.api.quimia.domain.safety.internal.web;

import com.api.quimia.domain.safety.dto.ComposicaoView;
import com.api.quimia.domain.safety.dto.SubstanciaView;
import com.api.quimia.domain.safety.internal.usecase.ConsultarComposicaoUseCase;
import com.api.quimia.domain.safety.internal.usecase.ConsultarSubstanciaUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consultas de segurança química do app (titular usuário). Tudo é leitura. */
@RestController
@RequestMapping("/api/v1")
public class SafetyController {
    private final ConsultarComposicaoUseCase composicao;
    private final ConsultarSubstanciaUseCase substancia;

    public SafetyController(ConsultarComposicaoUseCase composicao, ConsultarSubstanciaUseCase substancia) {
        this.composicao = composicao;
        this.substancia = substancia;
    }

    @GetMapping("/produtos/{produtoId}/substancias")
    public List<ComposicaoView> composicao(@PathVariable Integer produtoId) {
        return composicao.execute(produtoId);
    }

    @GetMapping("/substancias/{substanciaId}")
    public SubstanciaView substancia(@PathVariable Integer substanciaId) {
        return substancia.execute(substanciaId);
    }
}
