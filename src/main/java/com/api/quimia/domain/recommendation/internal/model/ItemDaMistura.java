package com.api.quimia.domain.recommendation.internal.model;

/**
 * Um lado da mistura: um produto ou uma substância da estante, nunca os dois. A ordem natural é a do histórico:
 * produto antes de substância e, no mesmo tipo, o menor id primeiro.
 */
public record ItemDaMistura(Integer produtoId, Integer substanciaId) implements Comparable<ItemDaMistura> {
    public static ItemDaMistura produto(Integer produtoId) {
        return new ItemDaMistura(produtoId, null);
    }

    public static ItemDaMistura substancia(Integer substanciaId) {
        return new ItemDaMistura(null, substanciaId);
    }

    public boolean ehProduto() {
        return produtoId != null;
    }

    public Integer id() {
        return ehProduto() ? produtoId : substanciaId;
    }

    @Override
    public int compareTo(ItemDaMistura outro) {
        if (ehProduto() != outro.ehProduto()) {
            return ehProduto() ? -1 : 1;
        }
        return id().compareTo(outro.id());
    }
}
