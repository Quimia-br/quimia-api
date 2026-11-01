package com.api.quimia.domain.safety.dto;

/** Um lado da mistura a avaliar: um produto ou uma substância, nunca os dois nem nenhum. */
public record ItemParaAvaliar(Integer produtoId, Integer substanciaId) {
    public ItemParaAvaliar {
        if ((produtoId == null) == (substanciaId == null)) {
            throw new IllegalArgumentException("item_invalido");
        }
    }

    public static ItemParaAvaliar produto(Integer produtoId) {
        return new ItemParaAvaliar(produtoId, null);
    }

    public static ItemParaAvaliar substancia(Integer substanciaId) {
        return new ItemParaAvaliar(null, substanciaId);
    }
}
