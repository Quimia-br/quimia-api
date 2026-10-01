package com.api.quimia.domain.account.internal.dto;

public record LocalizacaoResponse(
        String cep, String estado, String bairro, String rua, Integer numero, String complemento) {}
