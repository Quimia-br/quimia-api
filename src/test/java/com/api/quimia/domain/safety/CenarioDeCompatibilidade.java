package com.api.quimia.domain.safety;

import com.api.quimia.domain.safety.internal.model.ComposicaoDoProduto;
import com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade;
import com.api.quimia.domain.safety.internal.model.RegraDeIncompatibilidade;
import com.api.quimia.domain.safety.internal.model.Severidade;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Cenário aleatório e reproduzível (pela semente) para o motor de compatibilidade. Substâncias e classes usam a
 * mesma faixa de ids de propósito: um motor que confunda uma com a outra erra.
 */
record CenarioDeCompatibilidade(
        long semente,
        List<Integer> produtos,
        Map<Integer, ComposicaoDoProduto> composicoes,
        List<RegraDeIncompatibilidade> regras) {

    static CenarioDeCompatibilidade gerar(long semente) {
        SplittableRandom aleatorio = new SplittableRandom(semente);
        int totalSubstancias = 1 + aleatorio.nextInt(10);
        int totalClasses = 1 + aleatorio.nextInt(5);
        Map<Integer, Set<Integer>> classesDaSubstancia = new HashMap<>();
        for (int substancia = 1; substancia <= totalSubstancias; substancia++) {
            Set<Integer> classes = new HashSet<>();
            for (int classe = 1; classe <= totalClasses; classe++) {
                if (aleatorio.nextInt(10) < 3) {
                    classes.add(classe);
                }
            }
            classesDaSubstancia.put(substancia, classes);
        }

        Set<Integer> produtos = new LinkedHashSet<>();
        int totalProdutos = 2 + aleatorio.nextInt(7);
        while (produtos.size() < totalProdutos) {
            produtos.add(1 + aleatorio.nextInt(1000));
        }
        Map<Integer, ComposicaoDoProduto> composicoes = new HashMap<>();
        for (Integer produto : produtos) {
            int tipo = aleatorio.nextInt(10);
            if (tipo == 0) {
                continue;
            }
            if (tipo == 1) {
                composicoes.put(produto, new ComposicaoDoProduto(Set.of(), Set.of(), true));
                continue;
            }
            Set<Integer> substancias = new HashSet<>();
            int quantas = 1 + aleatorio.nextInt(4);
            for (int i = 0; i < quantas; i++) {
                substancias.add(1 + aleatorio.nextInt(totalSubstancias));
            }
            Set<Integer> classes = new HashSet<>();
            substancias.forEach(substancia -> classes.addAll(classesDaSubstancia.get(substancia)));
            composicoes.put(produto, new ComposicaoDoProduto(substancias, classes, aleatorio.nextInt(4) == 0));
        }

        List<RegraDeIncompatibilidade> regras = new ArrayList<>();
        int totalRegras = aleatorio.nextInt(13);
        int id = 0;
        Severidade[] severidades = Severidade.values();
        for (int i = 0; i < totalRegras; i++) {
            id += 1 + aleatorio.nextInt(5);
            Integer[] ladoA = lado(aleatorio, totalSubstancias, totalClasses);
            Integer[] ladoB = lado(aleatorio, totalSubstancias, totalClasses);
            regras.add(new RegraDeIncompatibilidade(
                    id, ladoA[0], ladoA[1], ladoB[0], ladoB[1],
                    severidades[aleatorio.nextInt(severidades.length)], "regra " + id));
        }
        embaralhar(regras, aleatorio);
        return new CenarioDeCompatibilidade(semente, List.copyOf(produtos), Map.copyOf(composicoes), List.copyOf(regras));
    }

    /** Substância (60%), classe (35%) ou lado vazio (5%, dado inválido que nunca deve casar). */
    private static Integer[] lado(SplittableRandom aleatorio, int totalSubstancias, int totalClasses) {
        int sorteio = aleatorio.nextInt(100);
        if (sorteio < 60) {
            return new Integer[] {1 + aleatorio.nextInt(totalSubstancias), null};
        }
        if (sorteio < 95) {
            return new Integer[] {null, 1 + aleatorio.nextInt(totalClasses)};
        }
        return new Integer[] {null, null};
    }

    static <T> void embaralhar(List<T> lista, SplittableRandom aleatorio) {
        for (int i = lista.size() - 1; i > 0; i--) {
            int j = aleatorio.nextInt(i + 1);
            T troca = lista.get(i);
            lista.set(i, lista.get(j));
            lista.set(j, troca);
        }
    }

    MotorDeCompatibilidade motor() {
        return new MotorDeCompatibilidade(composicoes, regras);
    }

    CenarioDeCompatibilidade comRegras(List<RegraDeIncompatibilidade> outras) {
        return new CenarioDeCompatibilidade(semente, produtos, composicoes, List.copyOf(outras));
    }

    CenarioDeCompatibilidade comComposicao(Integer produto, ComposicaoDoProduto composicao) {
        Map<Integer, ComposicaoDoProduto> outras = new HashMap<>(composicoes);
        outras.put(produto, composicao);
        return new CenarioDeCompatibilidade(semente, produtos, Map.copyOf(outras), regras);
    }

    ComposicaoDoProduto composicao(Integer produto) {
        return composicoes.getOrDefault(produto, ComposicaoDoProduto.VAZIA);
    }

    /**
     * Referência escrita por força bruta, separada do motor: a regra mais grave (empate pela menor id) entre as que
     * têm um lado em cada produto, em qualquer sentido.
     */
    Optional<RegraDeIncompatibilidade> piorRegraEntre(Integer x, Integer y) {
        ComposicaoDoProduto cx = composicao(x);
        ComposicaoDoProduto cy = composicao(y);
        return regras.stream()
                .filter(regra -> (temLado(regra.substanciaAId(), regra.classeAId(), cx)
                                && temLado(regra.substanciaBId(), regra.classeBId(), cy))
                        || (temLado(regra.substanciaAId(), regra.classeAId(), cy)
                                && temLado(regra.substanciaBId(), regra.classeBId(), cx)))
                .max(Comparator.comparing(RegraDeIncompatibilidade::severidade)
                        .thenComparing(Comparator.comparingInt(RegraDeIncompatibilidade::id).reversed()));
    }

    private static boolean temLado(Integer substancia, Integer classe, ComposicaoDoProduto composicao) {
        if (substancia != null && classe == null) {
            return composicao.substancias().contains(substancia);
        }
        if (classe != null && substancia == null) {
            return composicao.classes().contains(classe);
        }
        return false;
    }
}
