package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.AccountPrincipal;

/** `text` é a alternativa em texto puro de `html` para clientes que não renderizam HTML. */
public record AccountEmailRequestedEvent(
        AccountPrincipal principal, String to, String subject, String text, String html) {}
