package com.api.quimia.domain.insights.internal.usecase;

public class InsightsException extends RuntimeException {
    private final String code;
    private final int status;

    public InsightsException(String code, int status) {
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
