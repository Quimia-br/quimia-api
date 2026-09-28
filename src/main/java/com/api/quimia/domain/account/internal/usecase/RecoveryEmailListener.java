package com.api.quimia.domain.account.internal.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RecoveryEmailListener {
    private static final Logger log = LoggerFactory.getLogger(RecoveryEmailListener.class);

    private final RecoveryCodeSender sender;
    private final AuthenticationAuditRecorder audit;

    public RecoveryEmailListener(RecoveryCodeSender sender, AuthenticationAuditRecorder audit) {
        this.sender = sender;
        this.audit = audit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("recoveryEmailExecutor")
    public void send(RecoveryCodeRequestedEvent event) {
        try {
            boolean delivered = sender.send(event.userId(), event.email(), event.code());
            audit.record(event.userId(), delivered
                    ? "password_recovery_email_sent"
                    : "password_recovery_email_disabled");
        } catch (RuntimeException error) {
            audit.record(event.userId(), "password_recovery_email_failed");
            log.warn("Recovery email delivery failed user={}", event.userId());
        }
    }
}
