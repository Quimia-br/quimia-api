package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.dto.LoginResponse;

public record IssuedSession(LoginResponse response, String refreshToken) {}
