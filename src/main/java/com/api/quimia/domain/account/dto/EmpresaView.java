package com.api.quimia.domain.account.dto;

public record EmpresaView(Integer id, String nome, String email, String cnpj, String fotoUrl, boolean ativo) {}
