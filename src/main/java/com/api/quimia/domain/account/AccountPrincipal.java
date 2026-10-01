package com.api.quimia.domain.account;

import java.util.Locale;
import java.util.UUID;

/** Identidade autenticada: um registro de `usuario` (UUID) ou de `empresa` (INTEGER). */
public record AccountPrincipal(Type type, String id) {
    public enum Type {
        USUARIO,
        EMPRESA;

        public String claim() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Type fromClaim(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public UUID usuarioId() {
        requireType(Type.USUARIO);
        return UUID.fromString(id);
    }

    public Integer empresaId() {
        requireType(Type.EMPRESA);
        return Integer.valueOf(id);
    }

    private void requireType(Type expected) {
        if (type != expected) {
            throw new IllegalStateException("principal is " + type + ", expected " + expected);
        }
    }
}
