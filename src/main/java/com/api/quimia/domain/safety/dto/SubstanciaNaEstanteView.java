package com.api.quimia.domain.safety.dto;

import java.util.List;

/** Substância presente em algum dos produtos consultados, com os ids desses produtos. `descricao` pode ser nula. */
public record SubstanciaNaEstanteView(
        Integer id, String nome, String casNumero, String descricao, List<String> classes, List<Integer> produtoIds) {}
