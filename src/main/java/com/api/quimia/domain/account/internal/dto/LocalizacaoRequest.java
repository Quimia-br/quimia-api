package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Campos de `localizacao_usuario`. O schema não tem cidade; rua existe no banco. */
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
        @Size(max = 255) String complemento) {}
