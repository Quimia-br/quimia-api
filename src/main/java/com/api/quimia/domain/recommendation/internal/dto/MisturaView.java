package com.api.quimia.domain.recommendation.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import java.time.OffsetDateTime;

public record MisturaView(
        Integer id,
        ProdutoView produtoA,
        ProdutoView produtoB,
        String resultado,
        String severidade,
        String descricaoRisco,
        OffsetDateTime consultadoEm,
        DosagemView dosagem) {}
