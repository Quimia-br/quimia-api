package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.internal.model.CodigoBarras;

public class CatalogException extends RuntimeException {
    private final String code;
    private final int status;

    public CatalogException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    static CatalogException produtoNaoEncontrado() {
        return new CatalogException("produto_nao_encontrado", 404);
    }

    /** Normaliza o código ou lança `422 codigo_barras_invalido`; `null` quando vazio. */
    static String codigoBarras(String codigo) {
        try {
            return CodigoBarras.normalizar(codigo);
        } catch (IllegalArgumentException invalido) {
            throw new CatalogException("codigo_barras_invalido", 422);
        }
    }
}
