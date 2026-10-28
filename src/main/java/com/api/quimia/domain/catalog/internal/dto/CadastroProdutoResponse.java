package com.api.quimia.domain.catalog.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * `situacao`: `criado` (produto novo), `vinculado` (o código de barras já existia no catálogo e a
 * empresa foi vinculada a ele, sem alterar seus dados) ou `reativado` (vínculo excluído antes).
 */
@Schema(description = "Resultado do cadastro.")
public record CadastroProdutoResponse(
        @Schema(description = "Produto criado ou vinculado.")
        ProdutoView produto,
        @Schema(description = "criado (201), vinculado ou reativado (200).", example = "criado")
        String situacao) {}
