package com.api.quimia.domain.catalog.internal.model;

import java.util.regex.Pattern;

/** Código de barras do rótulo: só dígitos, sem limite de tamanho (o catálogo tem códigos fora do padrão EAN). */
public final class CodigoBarras {
    private static final Pattern SO_DIGITOS = Pattern.compile("\\d+");

    private CodigoBarras() {}

    /** Código sem espaços nas pontas, ou `null` quando vazio. Lança se tiver algo além de dígitos. */
    public static String normalizar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        String valor = codigo.trim();
        if (!SO_DIGITOS.matcher(valor).matches()) {
            throw new IllegalArgumentException("codigo_barras_invalido");
        }
        return valor;
    }
}
