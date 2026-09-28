package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.persistence.PasswordRecoveryRepository;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PasswordRecoveryCleanup {
    private final PasswordRecoveryRepository recoveries;

    public PasswordRecoveryCleanup(PasswordRecoveryRepository recoveries) {
        this.recoveries = recoveries;
    }

    @Scheduled(cron = "0 23 3 * * *")
    @Transactional
    public void removeExpiredChallenges() {
        recoveries.deleteExpiredBefore(OffsetDateTime.now().minusDays(1));
    }
}
