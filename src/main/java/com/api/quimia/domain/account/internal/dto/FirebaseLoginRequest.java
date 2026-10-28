package com.api.quimia.domain.account.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login social.")
public record FirebaseLoginRequest(
        @Schema(
                description = "ID token devolvido pelo Firebase Authentication no app.",
                example = "eyJhbGciOiJSUzI1NiIs...")
        @NotBlank String idToken) {}
