package com.api.quimia.domain.safety.dto;

/**
 * Resultado da compatibilidade de dois produtos. O par vem normalizado: `produtoIdA` é o menor id.
 * `resultado`: `compativel`, `incompativel` ou `nao_avaliado`. `severidade` e `regraId` só vêm quando
 * `incompativel`; `descricaoRisco` vem também no `nao_avaliado` (explica o motivo).
 */
public record AvaliacaoParView(
        Integer produtoIdA,
        Integer produtoIdB,
        String resultado,
        String severidade,
        String descricaoRisco,
        Integer regraId) {}
