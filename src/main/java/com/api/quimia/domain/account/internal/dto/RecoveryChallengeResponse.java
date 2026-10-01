package com.api.quimia.domain.account.internal.dto;

/** O desafio volta ao cliente porque o schema não guarda códigos de recuperação. */
public record RecoveryChallengeResponse(String challengeToken, long expiresIn) {}
