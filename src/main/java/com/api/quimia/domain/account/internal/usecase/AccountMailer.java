package com.api.quimia.domain.account.internal.usecase;

public interface AccountMailer {
    /** Envia HTML com alternativa em texto. Retorna `false` quando o envio está desativado na configuração. */
    boolean send(String to, String subject, String text, String html);
}
