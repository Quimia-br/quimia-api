package com.api.quimia.domain.account.internal.dto;

import com.api.quimia.domain.account.dto.UserSummary;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {}
