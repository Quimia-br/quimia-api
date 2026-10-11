package com.api.quimia.domain.storage;

import com.api.quimia.domain.safety.CompatibilidadeGateway;
import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Substitui o motor de `safety` nos testes de cômodo: o status é programado pelo teste. */
@TestConfiguration
public class CompatibilidadeDuble {
    public static volatile AvaliacaoConjuntoView proximaAvaliacao = seguro(2);
    public static volatile Collection<Integer> ultimosIds = List.of();

    public static AvaliacaoConjuntoView seguro(int produtos) {
        return new AvaliacaoConjuntoView("seguro", produtos, produtos * (produtos - 1) / 2, 0, 0, null, List.of());
    }

    public static AvaliacaoConjuntoView critico(int a, int b) {
        AvaliacaoParView par = new AvaliacaoParView(a, b, "incompativel", "critica", "Libera gás tóxico", 1);
        return new AvaliacaoConjuntoView("critico", 2, 1, 1, 0, "critica", List.of(par));
    }

    @Bean
    @Primary
    CompatibilidadeGateway compatibilidadeDuble() {
        return new CompatibilidadeGateway() {
            @Override
            public AvaliacaoParView avaliarPar(Integer a, Integer b) {
                throw new UnsupportedOperationException("não usado por storage");
            }

            @Override
            public AvaliacaoConjuntoView avaliarConjunto(Collection<Integer> produtoIds) {
                ultimosIds = List.copyOf(produtoIds);
                return proximaAvaliacao;
            }
        };
    }
}
