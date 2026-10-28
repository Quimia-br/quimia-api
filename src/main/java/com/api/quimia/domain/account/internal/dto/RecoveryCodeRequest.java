package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Código recebido por e-mail.")
public record RecoveryCodeRequest(
        @Schema(description = "E-mail da conta.", example = "maria@exemplo.com")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "Código de 4 dígitos.", example = "4821")
        @NotBlank @Pattern(regexp = "[0-9]{4}") String code,
        @Schema(description = "Token devolvido por `/auth/forgot-password`.", example = "c1a2b3...")
        @NotBlank @Size(max = 2048) String challengeToken) {}
