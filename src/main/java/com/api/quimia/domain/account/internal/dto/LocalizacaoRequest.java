package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Campos de `localizacao_usuario`. O schema não tem cidade; rua existe no banco. */
public record LocalizacaoRequest(
        @Pattern(regexp = "[0-9]{5}-?[0-9]{3}") String cep,
        @Pattern(regexp = "[A-Za-z]{2}") String estado,
        @Size(max = 255) String bairro,
        @Size(max = 255) String rua,
        @Positive Integer numero,
        @Size(max = 255) String complemento) {}
