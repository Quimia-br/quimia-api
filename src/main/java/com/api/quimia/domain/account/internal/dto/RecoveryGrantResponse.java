package com.api.quimia.domain.account.internal.dto;

public record RecoveryGrantResponse(String resetToken, long expiresIn) {}
