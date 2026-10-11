package com.api.quimia.domain.storage.internal.dto;

import java.util.List;

public record SubstanciaDaEstante(
        Integer id, String nome, String casNumero, String descricao, List<String> classes, List<ProdutoDaSubstancia> produtos) {}
