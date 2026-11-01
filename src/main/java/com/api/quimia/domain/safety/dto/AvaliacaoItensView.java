package com.api.quimia.domain.safety.dto;

/**
 * Resultado da compatibilidade de dois itens (produto ou substância). `resultado`: `compativel`, `incompativel` ou
 * `nao_avaliado`; `severidade` e `regraId` só vêm quando `incompativel` (`regraId` fica nulo quando a incompatibilidade
 * foi declarada pela FDS de um produto); `descricaoRisco` vem também no `nao_avaliado`, com o motivo.
 */
public record AvaliacaoItensView(String resultado, String severidade, String descricaoRisco, Integer regraId) {}
