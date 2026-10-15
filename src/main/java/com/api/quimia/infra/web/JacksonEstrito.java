package com.api.quimia.infra.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * O JSON da API respeita os tipos do contrato: `"1"` não vira inteiro (ex.: `produtoIds`) e número ou
 * booleano não viram texto (ex.: `codBarras`). Complementa `accept-float-as-int: false` do application.yml.
 * `""` em campo opcional (ex.: `dataNasc`, `numero`) continua virando `null`, como antes.
 */
@Configuration(proxyBeanMethods = false)
class JacksonEstrito {
    @Bean
    JsonMapperBuilderCustomizer escalaresSemCoercao() {
        return builder -> builder
                .withCoercionConfig(LogicalType.Integer, inteiro -> inteiro
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Textual, texto -> texto
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
