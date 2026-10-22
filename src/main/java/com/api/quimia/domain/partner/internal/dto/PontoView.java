package com.api.quimia.domain.partner.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ponto parceiro.")
public record PontoView(
        @Schema(description = "Id do ponto.", example = "8")
        Integer id,
        @Schema(description = "Nome.", example = "Ecoponto Pinheiros")
        String nome,
        @Schema(description = "compra ou descarte.", example = "descarte")
        String tipo,
        @Schema(description = "CEP.", example = "05422000")
        String cep,
        @Schema(description = "UF.", example = "SP")
        String estado,
        @Schema(description = "Bairro.", example = "Pinheiros")
        String bairro,
        @Schema(description = "Logradouro.", example = "Rua dos Pinheiros")
        String rua,
        @Schema(description = "Número.", example = "1200")
        Integer numero,
        @Schema(description = "Complemento.", example = "Portão 2")
        String complemento) {}
