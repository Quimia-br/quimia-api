package com.api.quimia.domain.recommendation.internal.usecase;

public class RecommendationException extends RuntimeException {
    private final String code;
    private final int status;

    public RecommendationException(String code, int status) {
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
