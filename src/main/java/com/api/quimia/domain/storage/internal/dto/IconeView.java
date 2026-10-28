package com.api.quimia.domain.storage.internal.dto;

import com.api.quimia.domain.storage.internal.model.IconeDeComodo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ícone que o app oferece para o cômodo.")
public record IconeView(
        @Schema(description = "Código que vai em `icone` ao criar o cômodo.", example = "toilet")
        String codigo,
        @Schema(description = "Texto do ícone na tela.", example = "Banheiro")
        String rotulo) {
    public static IconeView of(IconeDeComodo icone) {
        return new IconeView(icone.codigo(), icone.rotulo());
    }
}
