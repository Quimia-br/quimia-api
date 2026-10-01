package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** `nome` é obrigatório no schema mesmo ausente do protótipo web. */
public record EmpresaRegisterRequest(
        @NotBlank @Size(min = 2, max = 255) String nome,
        @NotBlank @Size(max = 20) String cnpj,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String senha) {}
