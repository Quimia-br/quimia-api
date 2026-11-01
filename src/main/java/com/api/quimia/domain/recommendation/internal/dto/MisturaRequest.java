package com.api.quimia.domain.recommendation.internal.dto;

import com.api.quimia.domain.recommendation.internal.model.ItemDaMistura;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

@Schema(description = "Par a misturar. Cada lado é um produto ou uma substância da estante: informe `produtoId*` ou "
        + "`substanciaId*` em cada lado, nunca os dois nem nenhum.")
public record MisturaRequest(
        @Schema(description = "Id de um produto da estante, no primeiro lado.", example = "42")
        Integer produtoIdA,
        @Schema(description = "Id de uma substância da estante (`GET /estante/substancias`), no primeiro lado.", example = "7")
        Integer substanciaIdA,
        @Schema(description = "Id de um produto da estante, no segundo lado.", example = "57")
        Integer produtoIdB,
        @Schema(description = "Id de uma substância da estante (`GET /estante/substancias`), no segundo lado.", example = "9")
        Integer substanciaIdB) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "informe produtoIdA ou substanciaIdA")
    public boolean isLadoAValido() {
        return (produtoIdA == null) != (substanciaIdA == null);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "informe produtoIdB ou substanciaIdB")
    public boolean isLadoBValido() {
        return (produtoIdB == null) != (substanciaIdB == null);
    }

    public ItemDaMistura itemA() {
        return new ItemDaMistura(produtoIdA, substanciaIdA);
    }

    public ItemDaMistura itemB() {
        return new ItemDaMistura(produtoIdB, substanciaIdB);
    }
}
