package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Substâncias da estante.")
public record SubstanciasDaEstanteResponse(
        @Schema(description = "Substâncias, por nome.")
        List<SubstanciaDaEstante> itens) {}
