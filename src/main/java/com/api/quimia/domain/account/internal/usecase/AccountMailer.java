package com.api.quimia.domain.account.internal.usecase;

public interface AccountMailer {
    /** Retorna `false` quando o envio está desativado na configuração. */
    boolean send(String to, String subject, String text);
}
