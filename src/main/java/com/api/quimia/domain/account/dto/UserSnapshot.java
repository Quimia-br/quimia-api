package com.api.quimia.domain.account.dto;

import com.api.quimia.domain.account.NivelAcesso;
import java.time.LocalDate;
import java.util.UUID;

public record UserSnapshot(UUID id, String email, NivelAcesso nivelAcesso, LocalDate dataNasc) {}
