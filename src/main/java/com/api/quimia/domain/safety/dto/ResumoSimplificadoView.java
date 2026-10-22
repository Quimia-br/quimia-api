package com.api.quimia.domain.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Texto de `fds.resumo_simplificado`; `resumo`, `versao` e `dataAtualizacao` são nulos sem FDS ativa. */
@Schema(description = "Resumo do rótulo.")
public record ResumoSimplificadoView(
        @Schema(description = "Id do produto.", example = "42")
        Integer produtoId,
        @Schema(
                description = "Texto simplificado da FDS. Nulo sem FDS ou sem texto.",
                example = "Corrosivo. Não misture com amônia.")
        String resumo,
        @Schema(description = "Versão da FDS.", example = "3")
        String versao,
        @Schema(description = "Data da FDS.", example = "2026-01-15")
        LocalDate dataAtualizacao) {}
