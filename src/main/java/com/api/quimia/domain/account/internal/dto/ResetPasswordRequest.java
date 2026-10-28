package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Troca de senha.")
public record ResetPasswordRequest(
        @Schema(description = "Token de troca: do código (app) ou do link do e-mail (portal).", example = "r9x8...")
        @NotBlank @Size(max = 2048) String resetToken,
        @Schema(description = "Nova senha com 8 caracteres ou mais, com letra e número.", example = "NovaSenha2026")
        @NotBlank @Size(min = 8, max = 72) String novaSenha) {}
