package com.api.quimia.domain.account.dto;

import com.api.quimia.domain.account.NivelAcesso;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Perfil do usuário.")
public record UserView(
        @Schema(description = "Id do usuário.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,
        @Schema(description = "Nome.", example = "Maria Souza")
        String nome,
        @Schema(description = "E-mail.", example = "maria@exemplo.com")
        String email,
        @Schema(description = "Data de nascimento.", example = "1990-05-20")
        LocalDate dataNasc,
        @Schema(
                description = "URL https da foto (a do Google no login social, se não houver outra).",
                example = "https://lh3.googleusercontent.com/a/foto")
        String fotoUrl,
        @Schema(description = "Perfil da conta.", example = "USUARIO")
        NivelAcesso nivelAcesso,
        @Schema(description = "Último login.", example = "2026-10-22T10:15:30-03:00")
        OffsetDateTime ultimaSessao) {}
