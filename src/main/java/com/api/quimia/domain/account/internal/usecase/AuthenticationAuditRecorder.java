package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.AccountPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Auditoria mínima em log estruturado; o schema não tem tabela de auditoria. Nunca registra email ou senha. */
@Component
public class AuthenticationAuditRecorder {
    private static final Logger log = LoggerFactory.getLogger("account.audit");

    public void record(String event) {
        log.info("auth_event={} principal_type=- principal_id=-", event);
    }

    public void record(String event, AccountPrincipal principal) {
        log.info("auth_event={} principal_type={} principal_id={}", event, principal.type(), principal.id());
    }
}
