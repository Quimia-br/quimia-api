package com.api.quimia.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Perfil da empresa.")
public record EmpresaView(
        @Schema(description = "Id da empresa.", example = "12")
        Integer id,
        @Schema(description = "Nome.", example = "Limpa Bem Ltda")
        String nome,
        @Schema(description = "E-mail.", example = "contato@limpabem.com.br")
        String email,
        @Schema(description = "CNPJ, só dígitos.", example = "11222333000181")
        String cnpj,
        @Schema(description = "URL https do logo.", example = "https://res.cloudinary.com/quimia/image/upload/logo.png")
        String fotoUrl,
        @Schema(description = "Empresa ativa (desativada não usa o portal).", example = "true")
        boolean ativo) {}
