package com.api.quimia;

import java.text.Normalizer;

/** Equivalentes no H2 de funções do PostgreSQL usadas pela API (registradas no INIT da URL de teste). */
public final class H2Functions {
    private H2Functions() {}

    public static String unaccent(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Sem efeito: o H2 não tem advisory lock; a serialização real só é exercitada no PostgreSQL. */
    public static void pgAdvisoryXactLock(long key) {}
}
