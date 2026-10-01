package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.AccountPrincipal;

public record AccountEmailRequestedEvent(AccountPrincipal principal, String to, String subject, String text) {}
