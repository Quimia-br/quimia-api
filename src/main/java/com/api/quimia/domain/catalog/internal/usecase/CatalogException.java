package com.api.quimia.domain.catalog.internal.usecase;

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
}
