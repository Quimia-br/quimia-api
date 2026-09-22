package com.api.quimia.domain.account.internal.usecase;

public class AccountException extends RuntimeException {
    private final String code;
    private final int status;

    public AccountException(String code, int status) {
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
