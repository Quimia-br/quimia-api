package com.api.quimia.domain.storage.internal.usecase;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Nome de cômodo: `trim`, espaços internos colapsados; a comparação ignora acento e caixa. */
final class NomeDeComodo {
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    private NomeDeComodo() {}

    static String normalizar(String nome) {
        return nome == null ? "" : ESPACOS.matcher(nome.trim()).replaceAll(" ");
    }

    static String chave(String nome) {
        return TextoBusca.normalizar(normalizar(nome));
    }

    /** Chave do advisory lock: serializa só o par (usuário, nome). */
    static long chaveDeLock(UUID usuarioId, String nome) {
        return Objects.hash(usuarioId, chave(nome));
    }
}
