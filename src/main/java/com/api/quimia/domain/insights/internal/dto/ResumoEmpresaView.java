package com.api.quimia.domain.insights.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Home do portal (só agregados).")
public record ResumoEmpresaView(
        @Schema(description = "Vezes que produtos da empresa estão em estantes de usuários.", example = "128")
        long produtosEmEstantes,
        @Schema(description = "Misturas que envolvem produtos da empresa.")
        ResumoMisturas misturas,
        @Schema(description = "Produto cadastrado mais recente; nulo sem produtos.")
        ProdutoView produtoDestaque) {}
