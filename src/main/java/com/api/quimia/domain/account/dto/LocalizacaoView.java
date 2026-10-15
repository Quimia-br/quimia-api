package com.api.quimia.domain.account.dto;

/** Endereço cadastrado do usuário (`localizacao_usuario`); qualquer campo pode ser nulo. */
public record LocalizacaoView(String cep, String estado, String bairro, String rua, Integer numero, String complemento) {}
