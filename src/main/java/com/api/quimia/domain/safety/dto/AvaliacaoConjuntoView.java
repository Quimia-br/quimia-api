package com.api.quimia.domain.safety.dto;

import java.util.List;

/**
 * Avaliação de todos os pares de um conjunto de produtos. `status`: `seguro`, `atencao`, `critico` ou
 * `nao_avaliado`. `incompatibilidades` traz só os pares incompatíveis, do mais grave ao menos grave.
 */
public record AvaliacaoConjuntoView(
        String status,
        int totalProdutos,
        int paresAvaliados,
        int paresIncompativeis,
        int paresNaoAvaliados,
        String severidadeMaxima,
        List<AvaliacaoParView> incompatibilidades) {}
