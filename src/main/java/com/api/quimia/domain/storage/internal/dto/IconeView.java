package com.api.quimia.domain.storage.internal.dto;

import com.api.quimia.domain.storage.internal.model.IconeDeComodo;

public record IconeView(String codigo, String rotulo) {
    public static IconeView of(IconeDeComodo icone) {
        return new IconeView(icone.codigo(), icone.rotulo());
    }
}
