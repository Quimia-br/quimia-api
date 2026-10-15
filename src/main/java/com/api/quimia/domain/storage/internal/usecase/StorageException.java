package com.api.quimia.domain.storage.internal.usecase;

public class StorageException extends RuntimeException {
    private final String code;
    private final int status;

    public StorageException(String code, int status) {
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
}
