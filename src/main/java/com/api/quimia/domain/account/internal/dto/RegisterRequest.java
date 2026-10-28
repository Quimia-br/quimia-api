package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** `localizacao` é o segundo passo opcional do cadastro no app. */
@Schema(description = "Cadastro do usuário do app.")
public record RegisterRequest(
        @Schema(description = "Nome, de 2 a 255 caracteres.", example = "Maria Souza")
        @NotBlank @Size(min = 2, max = 255) String nome,
        @Schema(description = "E-mail, único entre os usuários.", example = "maria@exemplo.com")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(
                description = "Senha com 8 caracteres ou mais (até 72 bytes), com letra e número.",
                example = "Quimia2026")
        @NotBlank @Size(min = 8, max = 72) String senha,
        @Schema(description = "Data de nascimento (16 anos ou mais).", example = "1990-05-20")
        @Past LocalDate dataNasc,
        @Schema(description = "Endereço opcional (segundo passo do cadastro).")
        @Valid LocalizacaoRequest localizacao) {}
