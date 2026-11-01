package com.api.quimia.domain.recommendation.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Consulta de mistura. Cada lado traz um produto ou uma substância: o outro campo vem nulo. "
        + "O par vem ordenado, produto antes de substância e, no mesmo tipo, o menor id primeiro.")
public record MisturaView(
        @Schema(description = "Id da consulta.", example = "15")
        Integer id,
        @Schema(description = "Produto do primeiro lado; nulo quando o lado é uma substância.")
        ProdutoView produtoA,
        @Schema(description = "Produto do segundo lado; nulo quando o lado é uma substância.")
        ProdutoView produtoB,
        @Schema(description = "Substância do primeiro lado; nula quando o lado é um produto.")
        SubstanciaDaMistura substanciaA,
        @Schema(description = "Substância do segundo lado; nula quando o lado é um produto.")
        SubstanciaDaMistura substanciaB,
        @Schema(description = "compativel, incompativel ou nao_avaliado.", example = "incompativel")
        String resultado,
        @Schema(
                description = "Severidade da regra mais grave: baixa, media, alta ou critica. Nula quando não é "
                        + "incompatível.",
                example = "critica")
        String severidade,
        @Schema(description = "Risco (incompativel) ou motivo (nao_avaliado).", example = "Libera gás cloro tóxico.")
        String descricaoRisco,
        @Schema(description = "Momento da consulta.", example = "2026-10-22T10:15:30-03:00")
        OffsetDateTime consultadoEm,
        @Schema(description = "Dosagem recomendada; sempre nula nesta versão (depende do agente).")
        DosagemView dosagem) {}
