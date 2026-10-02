package com.api.quimia.domain.catalog.internal.model;

import java.text.Normalizer;
import java.util.Locale;

/** Converte o termo digitado no padrão LIKE comparado com lower(unaccent(coluna)). */
public final class TermoBusca {
    public static final char ESCAPE = '!';

    private TermoBusca() {}

    /** Minúsculas, sem acento e com `%`, `_` e `!` escapados; `null` quando vazio. */
    public static String like(String termo) {
        if (termo == null || termo.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(termo.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String escaped = normalized
                .replace(String.valueOf(ESCAPE), "" + ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
