package com.api.quimia.domain.safety.dto;

/**
 * Resultado da compatibilidade de dois produtos. O par vem normalizado: `produtoIdA` é o menor id.
 * `resultado`: `compativel`, `incompativel` ou `nao_avaliado`. `severidade` só vem quando `incompativel`, e `regraId`
 * também, exceto quando a incompatibilidade foi declarada pela própria FDS de um dos produtos (sem regra curada); `descricaoRisco` vem também no `nao_avaliado` (explica o motivo).
 */
public record AvaliacaoParView(
        Integer produtoIdA,
        Integer produtoIdB,
        String resultado,
        String severidade,
        String descricaoRisco,
        Integer regraId) {}
