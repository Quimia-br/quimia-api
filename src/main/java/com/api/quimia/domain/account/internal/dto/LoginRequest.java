package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "E-mail e senha.")
public record LoginRequest(
        @Schema(description = "E-mail da conta.", example = "maria@exemplo.com")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "Senha.", example = "Quimia2026")
        @NotBlank @Size(max = 72) String senha) {
    /** Remove os espaços das pontas antes do @Email, que roda sobre o valor já construído. */
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
