package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** O desafio volta ao cliente porque o schema não guarda códigos de recuperação. */
@Schema(description = "Tentativa de recuperação aberta.")
public record RecoveryChallengeResponse(
        @Schema(description = "Identifica a tentativa; volta junto com o código.", example = "c1a2b3...")
        String challengeToken,
        @Schema(description = "Validade do código, em segundos.", example = "900")
        long expiresIn) {}
