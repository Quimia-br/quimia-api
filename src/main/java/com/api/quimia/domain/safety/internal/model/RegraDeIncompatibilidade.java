package com.api.quimia.domain.safety.internal.model;

/**
 * Regra ativa como o motor a usa. Cada lado é uma substância ou uma classe química; um lado sem nenhum dos dois
 * nunca casa.
 */
public record RegraDeIncompatibilidade(
        int id,
        Integer substanciaAId,
        Integer classeAId,
        Integer substanciaBId,
        Integer classeBId,
        Severidade severidade,
        String descricaoRisco) {

    public static RegraDeIncompatibilidade de(IncompatibilidadeRegra regra) {
        return new RegraDeIncompatibilidade(
                regra.getId(),
                regra.getSubstanciaAId(),
                regra.getClasseAId(),
                regra.getSubstanciaBId(),
                regra.getClasseBId(),
                Severidade.doCodigo(regra.getSeveridade()),
                regra.getDescricaoRisco());
    }

    /** O lado A casa com `x` e o lado B com `y`, ou o contrário. */
    boolean casa(ComposicaoDoProduto x, ComposicaoDoProduto y) {
        return (ladoCasa(substanciaAId, classeAId, x) && ladoCasa(substanciaBId, classeBId, y))
                || (ladoCasa(substanciaAId, classeAId, y) && ladoCasa(substanciaBId, classeBId, x));
    }

    private static boolean ladoCasa(Integer substancia, Integer classe, ComposicaoDoProduto composicao) {
        if (substancia != null) {
            return composicao.substancias().contains(substancia);
        }
        return classe != null && composicao.classes().contains(classe);
    }
}
