package com.api.quimia.domain.storage.internal.dto;

/** `status`: `adicionado`, `ja_na_estante` ou `nao_encontrado`; `nome` vem nulo quando não encontrado. */
public record ItemGuardado(Integer produtoId, String nome, String status) {}
