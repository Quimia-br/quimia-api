package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.model.AuthenticationAudit;
import com.api.quimia.domain.account.internal.persistence.AuthenticationAuditRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationAuditRecorder {
    private final AuthenticationAuditRepository audits;

    public AuthenticationAuditRecorder(AuthenticationAuditRepository audits) {
        this.audits = audits;
    }

    public void record(UUID userId, String event) {
        AuthenticationAudit audit = new AuthenticationAudit();
        audit.setUserId(userId);
        audit.setEvento(event);
        audit.setCreatedAt(OffsetDateTime.now());
        audits.save(audit);
    }
}
