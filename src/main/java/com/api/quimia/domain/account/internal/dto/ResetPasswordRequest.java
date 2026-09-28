package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 128) String resetToken,
        @NotBlank @Size(min = 10, max = 255) String novaSenha) {}
