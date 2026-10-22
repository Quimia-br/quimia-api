package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Autorização para trocar a senha.")
public record RecoveryGrantResponse(
        @Schema(description = "Token de uso único para `/auth/reset-password`.", example = "r9x8...")
        String resetToken,
        @Schema(description = "Validade do token, em segundos.", example = "600")
        long expiresIn) {}
