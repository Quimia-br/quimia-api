package com.api.quimia.domain.kpi.internal.usecase;

public class KpiException extends RuntimeException {
    private final String code;
    private final int status;

    public KpiException(String code, int status) {
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
