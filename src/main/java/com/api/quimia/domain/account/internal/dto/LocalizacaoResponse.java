package com.api.quimia.domain.account.internal.dto;

import java.math.BigDecimal;

public record LocalizacaoResponse(
        String cep,
        String estado,
        String bairro,
        String rua,
        Integer numero,
        String complemento,
        BigDecimal latitude,
        BigDecimal longitude) {}
