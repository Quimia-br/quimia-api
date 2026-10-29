package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Campo ausente (nulo) fica como está; os dois ausentes não alteram nada. */
@Schema(description = "Alteração do cômodo; campo ausente fica como está.")
public record ComodoEdicaoRequest(
        @Schema(
                description = "Novo nome, até 100 caracteres e não em branco; único por usuário, sem acento nem caixa.",
                example = "Lavanderia")
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") String nome,
        @Schema(
                description = "Código de um ícone de `GET /comodos/icones` (ex.: toilet, sofa, laundry).",
                example = "laundry")
        @Size(max = 50) @Pattern(regexp = "[a-z0-9_-]+") String icone) {}
