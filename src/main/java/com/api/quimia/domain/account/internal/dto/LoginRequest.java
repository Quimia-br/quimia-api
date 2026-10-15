package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String senha) {
    /** Remove os espaços das pontas antes do @Email, que roda sobre o valor já construído. */
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
