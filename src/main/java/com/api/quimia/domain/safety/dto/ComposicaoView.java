package com.api.quimia.domain.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Uma linha da composição do produto (`fds_composto`) com a substância resolvida e suas classes. */
@Schema(description = "Substância na composição do produto.")
public record ComposicaoView(
        @Schema(description = "Id da substância.", example = "7")
        Integer substanciaId,
        @Schema(description = "Nome canônico.", example = "Hipoclorito de sódio")
        String nome,
        @Schema(description = "Número CAS.", example = "7681-52-9")
        String casNumero,
        @Schema(description = "Concentração mínima.", example = "2.0")
        BigDecimal concentracaoMin,
        @Schema(description = "Concentração máxima.", example = "2.5")
        BigDecimal concentracaoMax,
        @Schema(description = "Unidade da concentração.", example = "%")
        String unidadeConcentracao,
        @Schema(description = "Classes químicas da substância.")
        List<String> classes) {}
