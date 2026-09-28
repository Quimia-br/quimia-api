package com.api.quimia.domain.account.internal.usecase;

import java.util.UUID;

public interface RecoveryCodeSender {
    boolean send(UUID userId, String email, String code);
}
