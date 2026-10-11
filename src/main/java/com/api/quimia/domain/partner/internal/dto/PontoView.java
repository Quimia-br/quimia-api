package com.api.quimia.domain.partner.internal.dto;

public record PontoView(
        Integer id, String nome, String tipo, String cep, String estado, String bairro, String rua, Integer numero, String complemento) {}
