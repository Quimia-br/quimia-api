package com.api.quimia.domain.account.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recuperacao_senha")
public class PasswordRecovery {
    @Id
    private UUID id;

    @Column(name = "id_usuario", nullable = false)
    private UUID userId;

    @Column(name = "codigo_hash", length = 64)
    private String codeHash;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "tentativas", nullable = false)
    private int attempts;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "codigo_consumido_em")
    private OffsetDateTime codeConsumedAt;

    @Column(name = "token_reset_hash", length = 64)
    private String resetTokenHash;

    @Column(name = "token_reset_expira_em")
    private OffsetDateTime resetTokenExpiresAt;

    @Column(name = "concluido_em")
    private OffsetDateTime completedAt;

    @Column(name = "revogado_em")
    private OffsetDateTime revokedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getCodeConsumedAt() {
        return codeConsumedAt;
    }

    public void setCodeConsumedAt(OffsetDateTime codeConsumedAt) {
        this.codeConsumedAt = codeConsumedAt;
    }

    public String getResetTokenHash() {
        return resetTokenHash;
    }

    public void setResetTokenHash(String resetTokenHash) {
        this.resetTokenHash = resetTokenHash;
    }

    public OffsetDateTime getResetTokenExpiresAt() {
        return resetTokenExpiresAt;
    }

    public void setResetTokenExpiresAt(OffsetDateTime resetTokenExpiresAt) {
        this.resetTokenExpiresAt = resetTokenExpiresAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
}
