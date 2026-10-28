package com.api.quimia.domain.account.internal.dto;

import com.api.quimia.domain.account.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sessão do app.")
public record MobileLoginResponse(
        @Schema(
                description = "Access token JWT, enviado como `Authorization: Bearer`.",
                example = "eyJhbGciOiJSUzI1NiIs...")
        String accessToken,
        @Schema(description = "Sempre Bearer.", example = "Bearer")
        String tokenType,
        @Schema(description = "Validade do access token, em segundos.", example = "900")
        long expiresIn,
        @Schema(description = "Refresh token de uso único, para `/auth/mobile/refresh`.", example = "k2P9...")
        String refreshToken,
        @Schema(description = "Usuário da sessão.")
        UserSummary user) {}
