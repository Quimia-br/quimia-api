package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Campos de `localizacao_usuario`. O schema não tem cidade; rua existe no banco. `latitude` e `longitude` (graus
 * decimais, arredondados a 6 casas) vêm juntas ou não vêm: só uma das duas responde 422 `invalid_coordinates`. Como o
 * endereço é substituído por inteiro, sem coordenadas o par fica nulo.
 */
public record LocalizacaoRequest(
        @Pattern(regexp = "[0-9]{5}-?[0-9]{3}") String cep,
        @Pattern(regexp = "[A-Za-z]{2}") String estado,
        @Size(max = 255) String bairro,
        @Size(max = 255) String rua,
        @Positive Integer numero,
        @Size(max = 255) String complemento,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}
