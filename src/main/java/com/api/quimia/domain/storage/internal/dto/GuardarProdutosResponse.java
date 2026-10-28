package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Resultado por produto.")
public record GuardarProdutosResponse(
        @Schema(description = "Um item por id enviado.")
        List<ItemGuardado> itens) {}
