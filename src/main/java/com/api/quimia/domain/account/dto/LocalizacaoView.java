package com.api.quimia.domain.account.dto;

import java.math.BigDecimal;

/** Endereço cadastrado do usuário (`localizacao_usuario`); qualquer campo pode ser nulo. Coordenadas vêm juntas. */
public record LocalizacaoView(
        String cep,
        String estado,
        String bairro,
        String rua,
        Integer numero,
        String complemento,
        BigDecimal latitude,
        BigDecimal longitude) {}
