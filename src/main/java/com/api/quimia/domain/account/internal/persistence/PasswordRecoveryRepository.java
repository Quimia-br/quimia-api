package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.internal.model.PasswordRecovery;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordRecoveryRepository extends JpaRepository<PasswordRecovery, UUID> {
    @Modifying
    @Transactional
    @Query("delete from PasswordRecovery recovery where recovery.createdAt < :createdAt")
    int deleteExpiredBefore(@Param("createdAt") OffsetDateTime createdAt);

    long countByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordRecovery> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordRecovery> findFirstByUserIdAndRevokedAtIsNullAndCompletedAtIsNullOrderByCreatedAtDesc(
            UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordRecovery> findByResetTokenHash(String resetTokenHash);

    @Query("select recovery.userId from PasswordRecovery recovery where recovery.resetTokenHash = :resetTokenHash")
    Optional<UUID> findUserIdByResetTokenHash(@Param("resetTokenHash") String resetTokenHash);
}
