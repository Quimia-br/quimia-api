package com.api.quimia.domain.account.dto;

import com.api.quimia.domain.account.NivelAcesso;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserView(
        UUID id,
        String nome,
        String email,
        LocalDate dataNasc,
        NivelAcesso nivelAcesso,
        OffsetDateTime ultimaSessao) {}
