package com.api.quimia.domain.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Classe química.")
public record ClasseQuimicaView(
        @Schema(description = "Id da classe.", example = "2")
        Integer id,
        @Schema(description = "Nome.", example = "Oxidantes")
        String nome,
        @Schema(description = "Descrição.", example = "Substâncias que cedem oxigênio.")
        String descricao) {}
