package com.api.quimia.domain.storage.internal.usecase;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Comparação de texto sem acento e sem diferenciar caixa, feita em memória (listas pequenas). */
final class TextoBusca {
    private static final Pattern MARCAS_DE_ACENTO = Pattern.compile("\\p{M}");

    private TextoBusca() {}

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = MARCAS_DE_ACENTO
                .matcher(Normalizer.normalize(texto.trim(), Normalizer.Form.NFD))
                .replaceAll("");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    /** `termo` nulo ou em branco casa tudo. */
    static boolean contem(String texto, String termo) {
        String procurado = normalizar(termo);
        return procurado.isEmpty() || normalizar(texto).contains(procurado);
    }
}
