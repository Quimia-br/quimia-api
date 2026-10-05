package com.api.quimia.domain.safety.dto;

import java.math.BigDecimal;
import java.util.List;

/** Uma linha da composição do produto (`fds_composto`) com a substância resolvida e suas classes. */
public record ComposicaoView(
        Integer substanciaId,
        String nome,
        String casNumero,
        BigDecimal concentracaoMin,
        BigDecimal concentracaoMax,
        String unidadeConcentracao,
        List<String> classes) {}
