package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Renovação da sessão do app.")
public record RefreshRequest(
        @Schema(description = "Refresh token recebido no login ou na última renovação.", example = "k2P9...")
        String refreshToken) {}
