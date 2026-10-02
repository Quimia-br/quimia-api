package com.api.quimia.domain.storage.event;

import java.util.UUID;

/** Publicado quando um produto sai da estante; o módulo de misturas apagará o histórico do produto. */
public record ProdutoRemovidoDaEstanteEvent(UUID usuarioId, Integer produtoId) {}
