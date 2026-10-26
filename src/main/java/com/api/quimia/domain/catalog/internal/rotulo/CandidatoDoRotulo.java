package com.api.quimia.domain.catalog.internal.rotulo;

/** Produto lido no rótulo. `marca` e `codBarras` vêm nulos quando o agente não os leu. */
public record CandidatoDoRotulo(String nome, String marca, String codBarras) {}
