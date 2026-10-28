package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** O mínimo de produtos (2) e o máximo (20) são regras do caso de uso, para devolver códigos de erro próprios. */
@Schema(description = "Novo cômodo.")
public record ComodoRequest(
        @Schema(
                description = "Nome, até 100 caracteres; único por usuário, sem acento nem caixa.",
                example = "Banheiro")
        @NotBlank @Size(max = 100) String nome,
        @Schema(
                description = "Código de um ícone de `GET /comodos/icones` (ex.: toilet, sofa, refrigerator).",
                example = "toilet")
        @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9_-]+") String icone,
        @Schema(description = "De 2 a 20 produtos da estante.", example = "[42, 57]")
        @NotNull List<@NotNull Integer> produtoIds) {}
