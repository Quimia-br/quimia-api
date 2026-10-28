package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Alteração parcial do perfil: só os campos enviados mudam.")
public record UpdateProfileRequest(
        @Schema(description = "Nome, de 2 a 255 caracteres.", example = "Maria S. Souza")
        @Size(min = 2, max = 255) String nome,
        @Schema(description = "Data de nascimento.", example = "1990-05-20")
        @Past LocalDate dataNasc,
        @Schema(description = "URL https da foto.", example = "https://exemplo.com/foto.jpg")
        @Size(max = FOTO_URL_MAXIMA) @Pattern(regexp = FOTO_URL_REGEX) String fotoUrl) {
    /** Tamanho de `usuario.foto_url`; também vale para a foto do login social. */
    public static final int FOTO_URL_MAXIMA = 450;
    /** URL https sem espaço; também vale para a foto do login social. */
    public static final String FOTO_URL_REGEX = "https://\\S+";
}
