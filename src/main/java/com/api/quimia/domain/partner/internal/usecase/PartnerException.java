package com.api.quimia.domain.partner.internal.usecase;

public class PartnerException extends RuntimeException {
    private final String code;
    private final int status;

    public PartnerException(String code, int status) {
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
