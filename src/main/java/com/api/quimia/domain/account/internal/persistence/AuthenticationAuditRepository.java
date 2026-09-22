package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.internal.model.AuthenticationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationAuditRepository extends JpaRepository<AuthenticationAudit, Long> {}
