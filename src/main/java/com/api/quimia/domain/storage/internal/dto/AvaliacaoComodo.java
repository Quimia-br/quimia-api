package com.api.quimia.domain.storage.internal.dto;

import java.util.List;

public record AvaliacaoComodo(
        String status,
        int totalProdutos,
        int paresAvaliados,
        int paresIncompativeis,
        String severidadeMaxima,
        List<ParIncompativel> incompatibilidades) {}
