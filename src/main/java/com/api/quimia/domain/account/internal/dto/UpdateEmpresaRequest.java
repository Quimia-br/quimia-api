package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Alteração parcial do perfil da empresa.")
public record UpdateEmpresaRequest(
        @Schema(description = "Nome.", example = "Limpa Bem Ltda")
        @Size(min = 2, max = 255) String nome,
        @Schema(description = "E-mail de acesso.", example = "contato@limpabem.com.br")
        @Email @Size(max = 255) String email,
        @Schema(
                description = "URL https do logo (para enviar o arquivo, use `POST /empresas/me/foto`).",
                example = "https://exemplo.com/logo.png")
        @Size(max = 200) @Pattern(regexp = "https://\\S+") String fotoUrl) {}
