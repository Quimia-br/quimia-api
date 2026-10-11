package com.api.quimia.domain.safety.internal.model;

import java.util.Set;

/**
 * Composição de um produto para o motor de compatibilidade: união dos compostos das FDS ativas.
 * `temCompostoNaoResolvido` indica composto com `id_substancia` nulo em alguma FDS ativa.
 */
public record ComposicaoDoProduto(Set<Integer> substancias, Set<Integer> classes, boolean temCompostoNaoResolvido) {
    public static final ComposicaoDoProduto VAZIA = new ComposicaoDoProduto(Set.of(), Set.of(), false);

    public boolean semSubstanciaResolvida() {
        return substancias.isEmpty();
    }
}
