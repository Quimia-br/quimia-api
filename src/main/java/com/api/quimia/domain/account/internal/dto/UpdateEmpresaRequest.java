package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEmpresaRequest(
        @Size(min = 2, max = 255) String nome,
        @Email @Size(max = 255) String email,
        @Size(max = 200) @Pattern(regexp = "https://\\S+") String fotoUrl) {}
