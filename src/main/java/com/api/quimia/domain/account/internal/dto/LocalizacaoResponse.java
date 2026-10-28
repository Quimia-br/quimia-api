package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Endereço do usuário.")
public record LocalizacaoResponse(
        @Schema(description = "CEP com 8 dígitos (com ou sem hífen na entrada).", example = "05422000")
        String cep,
        @Schema(description = "UF com 2 letras.", example = "SP")
        String estado,
        @Schema(description = "Bairro.", example = "Pinheiros")
        String bairro,
        @Schema(description = "Logradouro.", example = "Rua dos Pinheiros")
        String rua,
        @Schema(description = "Número.", example = "1200")
        Integer numero,
        @Schema(description = "Complemento.", example = "apto 42")
        String complemento,
        @Schema(description = "Latitude em graus decimais; nula se o endereço foi salvo sem coordenadas.",
                example = "-23.561414")
        BigDecimal latitude,
        @Schema(description = "Longitude em graus decimais; nula se o endereço foi salvo sem coordenadas.",
                example = "-46.655881")
        BigDecimal longitude) {}
