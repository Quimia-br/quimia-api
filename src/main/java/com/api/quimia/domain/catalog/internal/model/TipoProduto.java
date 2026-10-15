package com.api.quimia.domain.catalog.internal.model;

import java.util.Arrays;

/** Valores do CHECK de `produto.tipo_produto` no quimia-bd. O front exibe a tradução. */
public enum TipoProduto {
    LIMPEZA_GERAL("limpeza_geral"),
    DESINFETANTE("desinfetante"),
    DESINCRUSTANTE("desincrustante"),
    DESENGRAXANTE("desengraxante"),
    ALVEJANTE("alvejante"),
    AROMATIZANTE("aromatizante"),
    OUTRO("outro");

    private final String codigo;

    TipoProduto(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static boolean existe(String codigo) {
        return Arrays.stream(values()).anyMatch(tipo -> tipo.codigo.equals(codigo));
    }
}
