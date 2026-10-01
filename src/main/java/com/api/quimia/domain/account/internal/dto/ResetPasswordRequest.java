package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 2048) String resetToken,
        @NotBlank @Size(min = 8, max = 72) String novaSenha) {}
