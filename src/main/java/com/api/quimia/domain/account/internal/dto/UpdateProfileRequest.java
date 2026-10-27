package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * `PATCH /usuarios/me`: campo ausente mantém o valor atual. `removerDataNasc` = true apaga a data de nascimento (um
 * `dataNasc` nulo significa "não alterar"); não vale junto com `dataNasc`.
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 255) String nome,
        @Past LocalDate dataNasc,
        Boolean removerDataNasc,
        @Size(max = FOTO_URL_MAXIMA) @Pattern(regexp = FOTO_URL_REGEX) String fotoUrl) {
    /** Tamanho de `usuario.foto_url`; também vale para a foto do login social. */
    public static final int FOTO_URL_MAXIMA = 450;
    /** URL https sem espaço; também vale para a foto do login social. */
    public static final String FOTO_URL_REGEX = "https://\\S+";
}
