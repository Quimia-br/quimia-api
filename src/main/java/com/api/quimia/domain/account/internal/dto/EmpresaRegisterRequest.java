package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** `nome` é obrigatório no schema mesmo ausente do protótipo web. */
@Schema(description = "Cadastro da empresa.")
public record EmpresaRegisterRequest(
        @Schema(description = "Razão social ou nome fantasia.", example = "Limpa Bem Ltda")
        @NotBlank @Size(min = 2, max = 255) String nome,
        @Schema(description = "CNPJ com 14 dígitos (com ou sem máscara).", example = "11.222.333/0001-81")
        @NotBlank @Size(max = 20) String cnpj,
        @Schema(description = "E-mail de acesso ao portal, único.", example = "contato@limpabem.com.br")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "Senha com 8 caracteres ou mais, com letra e número.", example = "Empresa2026")
        @NotBlank @Size(min = 8, max = 72) String senha) {}
