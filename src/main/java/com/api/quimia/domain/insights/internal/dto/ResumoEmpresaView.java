package com.api.quimia.domain.insights.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;

public record ResumoEmpresaView(long produtosEmEstantes, ResumoMisturas misturas, ProdutoView produtoDestaque) {}
