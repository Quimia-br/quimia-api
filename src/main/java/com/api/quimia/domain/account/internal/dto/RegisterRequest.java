package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** `localizacao` é o segundo passo opcional do cadastro no app. */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 255) String nome,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String senha,
        @Past LocalDate dataNasc,
        @Valid LocalizacaoRequest localizacao) {}
