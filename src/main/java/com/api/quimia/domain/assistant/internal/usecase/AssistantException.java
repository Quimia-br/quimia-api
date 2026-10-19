package com.api.quimia.domain.assistant.internal.usecase;

/** Erro do chat com código estável (`detail` do problem+json) e status HTTP. */
public class AssistantException extends RuntimeException {
    private final String code;
    private final int status;

    public AssistantException(String code, int status) {
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
