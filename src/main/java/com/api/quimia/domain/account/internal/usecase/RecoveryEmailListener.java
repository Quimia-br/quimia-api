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

    private final AccountMailer mailer;
    private final AuthenticationAuditRecorder audit;

    public RecoveryEmailListener(AccountMailer mailer, AuthenticationAuditRecorder audit) {
        this.mailer = mailer;
        this.audit = audit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("recoveryEmailExecutor")
    public void send(AccountEmailRequestedEvent event) {
        try {
            boolean delivered = mailer.send(event.to(), event.subject(), event.text());
            audit.record(
                    delivered ? "password_recovery_email_sent" : "password_recovery_email_disabled",
                    event.principal());
        } catch (RuntimeException error) {
            audit.record("password_recovery_email_failed", event.principal());
            log.warn("Recovery email delivery failed principal={}", event.principal().id());
        }
    }
}
