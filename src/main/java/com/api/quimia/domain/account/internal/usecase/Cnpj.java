package com.api.quimia.domain.account.internal.usecase;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CNPJ numérico ou alfanumérico (Receita Federal, a partir de julho de 2026): 12 caracteres
 * [0-9A-Z] seguidos de 2 dígitos verificadores calculados por módulo 11 sobre (ASCII - 48).
 */
final class Cnpj {
    private static final Pattern FORMAT = Pattern.compile("[0-9A-Z]{12}[0-9]{2}");
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{13}");
    private static final int[] FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int MODULUS = 11;

    private Cnpj() {}

    /** Remove pontuação e padroniza maiúsculas; não valida. */
    static String normalize(String value) {
        return value.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
    }

    static boolean isValid(String normalized) {
        if (!FORMAT.matcher(normalized).matches() || REPEATED.matcher(normalized).matches()) {
            return false;
        }
        int first = checkDigit(normalized, FIRST_WEIGHTS);
        int second = checkDigit(normalized, SECOND_WEIGHTS);
        return normalized.charAt(12) - '0' == first && normalized.charAt(13) - '0' == second;
    }

    private static int checkDigit(String value, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (value.charAt(i) - '0') * weights[i];
        }
        int remainder = sum % MODULUS;
        return remainder < 2 ? 0 : MODULUS - remainder;
    }
}
