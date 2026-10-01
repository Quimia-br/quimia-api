package com.api.quimia.domain.account.internal.dto;

import com.api.quimia.domain.account.dto.EmpresaSummary;

public record EmpresaLoginResponse(String accessToken, String tokenType, long expiresIn, EmpresaSummary empresa) {}
