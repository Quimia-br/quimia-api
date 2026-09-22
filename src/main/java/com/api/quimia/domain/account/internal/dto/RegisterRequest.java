package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 255) String nome,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 10, max = 255) String senha,
        @Past LocalDate dataNasc) {}
