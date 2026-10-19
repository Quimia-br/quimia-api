package com.api.quimia.domain.safety.internal.model;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Severidade de uma regra de incompatibilidade, da menos para a mais grave. */
public enum Severidade {
    BAIXA("baixa"),
    MEDIA("media"),
    ALTA("alta"),
    CRITICA("critica");

    private static final Logger log = LoggerFactory.getLogger(Severidade.class);

    private final String codigo;

    Severidade(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    /** `alta` e `critica` deixam o conjunto crítico; `baixa` e `media`, em atenção. */
    public boolean critica() {
        return compareTo(ALTA) >= 0;
    }

    /**
     * O CHECK do banco só aceita os quatro códigos. Se outro código aparecer (dado corrompido ou regra nova sem
     * migração), a regra vale como crítica: um risco desconhecido nunca é tratado como leve.
     */
    public static Severidade doCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(severidade -> severidade.codigo.equals(codigo))
                .findFirst()
                .orElseGet(() -> {
                    log.error("severidade desconhecida em incompatibilidade_regra: {}; tratada como critica", codigo);
                    return CRITICA;
                });
    }
}
