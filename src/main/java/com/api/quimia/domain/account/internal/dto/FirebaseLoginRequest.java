package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record FirebaseLoginRequest(@NotBlank String idToken) {}
