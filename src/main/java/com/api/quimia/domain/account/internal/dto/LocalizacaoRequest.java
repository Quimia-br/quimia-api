package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Endereço do usuário.")
public record LocalizacaoRequest(
        @Schema(description = "CEP com 8 dígitos (com ou sem hífen na entrada).", example = "05422000")
        @Pattern(regexp = "[0-9]{5}-?[0-9]{3}") String cep,
        @Schema(description = "UF com 2 letras.", example = "SP")
        @Pattern(regexp = "[A-Za-z]{2}") String estado,
        @Schema(description = "Bairro.", example = "Pinheiros")
        @Size(max = 255) String bairro,
        @Schema(description = "Logradouro.", example = "Rua dos Pinheiros")
        @Size(max = 255) String rua,
        @Schema(description = "Número.", example = "1200")
        @Positive Integer numero,
        @Schema(description = "Complemento.", example = "apto 42")
        @Size(max = 255) String complemento,
        @Schema(description = "Latitude em graus decimais, de -90 a 90 (6 casas). Vem junto com `longitude`.",
                example = "-23.561414")
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @Schema(description = "Longitude em graus decimais, de -180 a 180 (6 casas). Vem junto com `latitude`.",
                example = "-46.655881")
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}
