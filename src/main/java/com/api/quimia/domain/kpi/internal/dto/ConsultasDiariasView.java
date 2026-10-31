package com.api.quimia.domain.kpi.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Consultas aos produtos da empresa em um dia.")
public record ConsultasDiariasView(
        @Schema(description = "Dia.", example = "2026-10-28")
        LocalDate data,
        @Schema(description = "Consultas aos produtos da empresa.", example = "40")
        long totalConsultas,
        @Schema(description = "Usuários distintos que consultaram.", example = "12")
        long usuariosDistintos) {}
