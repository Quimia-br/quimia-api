package com.api.quimia.domain.safety.internal.model;

/**
 * Incompatibilidade que a FDS ativa de `produtoId` declara com uma substância ou classe química. Atinge outro produto
 * que tenha essa substância ou essa classe; um lado sem nenhum dos dois nunca atinge.
 */
public record IncompatibilidadeDaFds(
        int produtoId, Integer substanciaId, Integer classeId, Severidade severidade, String descricaoRisco) {

    boolean atinge(ComposicaoDoProduto outro) {
        if (substanciaId != null && outro.substancias().contains(substanciaId)) {
            return true;
        }
        return classeId != null && outro.classes().contains(classeId);
    }
}
