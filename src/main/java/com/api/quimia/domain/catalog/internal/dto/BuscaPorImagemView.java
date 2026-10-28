package com.api.quimia.domain.catalog.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Produtos do catálogo reconhecidos no rótulo fotografado.")
public record BuscaPorImagemView(
        @Schema(description = "Produtos do catálogo que o agente leu na imagem, sem repetir nenhum.")
        List<ProdutoView> produtos,
        @Schema(
                description = "Quantos produtos lidos no rótulo não casaram com nenhum produto do catálogo.",
                example = "1")
        int naoIdentificados) {}
