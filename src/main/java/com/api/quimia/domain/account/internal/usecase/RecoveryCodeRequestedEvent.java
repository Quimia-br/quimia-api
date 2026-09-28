package com.api.quimia.domain.account.internal.usecase;

import java.util.UUID;

public record RecoveryCodeRequestedEvent(UUID userId, String email, String code) {}
