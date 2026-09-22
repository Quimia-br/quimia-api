package com.api.quimia.domain.account.internal.dto;

import com.api.quimia.domain.account.dto.UserSummary;

public record MobileLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        UserSummary user) {}
