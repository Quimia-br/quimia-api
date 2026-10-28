package com.api.quimia.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Resumo do usuário.")
public record UserSummary(
        @Schema(description = "Id do usuário.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,
        @Schema(description = "Nome.", example = "Maria Souza")
        String nome,
        @Schema(description = "E-mail.", example = "maria@exemplo.com")
        String email) {}
