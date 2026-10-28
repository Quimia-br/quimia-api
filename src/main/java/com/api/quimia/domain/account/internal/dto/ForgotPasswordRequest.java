package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Pedido de recuperação.")
public record ForgotPasswordRequest(
        @Schema(description = "E-mail da conta.", example = "maria@exemplo.com")
        @NotBlank @Email @Size(max = 255) String email) {}
