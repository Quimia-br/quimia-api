package com.api.quimia.domain.account.internal.dto;

import com.api.quimia.domain.account.dto.EmpresaSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sessão do portal (o refresh vai no cookie `quimia_rt`).")
public record EmpresaLoginResponse(
        @Schema(
                description = "Access token JWT, enviado como `Authorization: Bearer`.",
                example = "eyJhbGciOiJSUzI1NiIs...")
        String accessToken,
        @Schema(description = "Sempre Bearer.", example = "Bearer")
        String tokenType,
        @Schema(description = "Validade do access token, em segundos.", example = "900")
        long expiresIn,
        @Schema(description = "Empresa da sessão.")
        EmpresaSummary empresa) {}
