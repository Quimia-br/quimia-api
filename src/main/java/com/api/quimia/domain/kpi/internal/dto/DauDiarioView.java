package com.api.quimia.domain.kpi.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Usuários ativos da empresa em um dia: quem consultou produtos dela. Campos estatísticos vêm ausentes "
        + "quando não há base para o cálculo (primeiro dia, um dia só, desvio zero).")
public record DauDiarioView(
        @Schema(description = "Dia.", example = "2026-10-28")
        LocalDate data,
        @Schema(description = "Usuários distintos que consultaram produtos da empresa no dia.", example = "12")
        long usuariosAtivos,
        @Schema(description = "Diferença para o dia anterior com consulta. Ausente no primeiro dia.", example = "3")
        Long variacaoUsuarios,
        @Schema(description = "Variação percentual para o dia anterior. Ausente no primeiro dia.", example = "33.33")
        Double variacaoPct,
        @Schema(description = "Média de usuários ativos de todos os dias.", example = "9.5")
        Double mediaGlobal,
        @Schema(description = "Média dos últimos 7 dias com consulta, contando o atual.", example = "10.2")
        Double media7d,
        @Schema(description = "Desvio padrão amostral de todos os dias. Ausente com menos de dois dias.", example = "2.4")
        Double desvioPadrao,
        @Schema(description = "Distância do dia à média, em desvios padrão. Ausente sem desvio padrão.", example = "1.04")
        Double zScore) {}
