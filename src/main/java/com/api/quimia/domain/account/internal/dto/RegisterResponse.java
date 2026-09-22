package com.api.quimia.domain.account.internal.dto;

import com.api.quimia.domain.account.NivelAcesso;
import java.util.UUID;

public record RegisterResponse(UUID id, String nome, String email, NivelAcesso nivelAcesso) {}
