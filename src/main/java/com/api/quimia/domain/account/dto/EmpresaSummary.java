package com.api.quimia.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo da empresa.")
public record EmpresaSummary(
        @Schema(description = "Id da empresa.", example = "12")
        Integer id,
        @Schema(description = "Nome.", example = "Limpa Bem Ltda")
        String nome,
        @Schema(description = "E-mail.", example = "contato@limpabem.com.br")
        String email) {}
