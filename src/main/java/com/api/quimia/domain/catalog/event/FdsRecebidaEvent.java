package com.api.quimia.domain.catalog.event;

/**
 * Publicado, dentro da transação do cadastro ou da alteração do produto, quando a FDS enviada pelo portal foi
 * extraída. `rawJson` é a resposta do extrator, já validada; o módulo de segurança química grava a nova FDS ativa.
 */
public record FdsRecebidaEvent(Integer produtoId, String rawJson) {}
