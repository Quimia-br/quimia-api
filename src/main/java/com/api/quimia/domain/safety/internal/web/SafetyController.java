package com.api.quimia.domain.safety.internal.web;

import com.api.quimia.domain.safety.dto.ComposicaoView;
import com.api.quimia.domain.safety.dto.SubstanciaView;
import com.api.quimia.domain.safety.internal.usecase.ConsultarComposicaoUseCase;
import com.api.quimia.domain.safety.internal.usecase.ConsultarSubstanciaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consultas de segurança química do app (titular usuário). Tudo é leitura. */
@Tag(name = "Segurança química")
@RestController
@RequestMapping("/api/v1")
public class SafetyController {
    private final ConsultarComposicaoUseCase composicao;
    private final ConsultarSubstanciaUseCase substancia;

    public SafetyController(ConsultarComposicaoUseCase composicao, ConsultarSubstanciaUseCase substancia) {
        this.composicao = composicao;
        this.substancia = substancia;
    }

    @Operation(
            summary = "Substâncias de um produto",
            description = "Composição da FDS ativa mais recente, por nome da substância, com concentração e "
                    + "classes químicas. Lista vazia quando o produto não tem FDS.")
    @Parameter(name = "produtoId", description = "Id do produto.", example = "42")
    @ApiResponse(responseCode = "200", description = "Composição.")
    @ApiResponse(responseCode = "404", description = "Produto inexistente: `produto_nao_encontrado`.")
    @GetMapping("/produtos/{produtoId}/substancias")
    public List<ComposicaoView> composicao(@PathVariable Integer produtoId) {
        return composicao.execute(produtoId);
    }

    @Operation(summary = "Detalhe da substância", description = "Nome, CAS, descrição, sinônimos e classes químicas.")
    @Parameter(name = "substanciaId", description = "Id da substância.", example = "7")
    @ApiResponse(responseCode = "200", description = "Substância.")
    @ApiResponse(responseCode = "404", description = "Substância inexistente: `substancia_nao_encontrada`.")
    @GetMapping("/substancias/{substanciaId}")
    public SubstanciaView substancia(@PathVariable Integer substanciaId) {
        return substancia.execute(substanciaId);
    }
}
