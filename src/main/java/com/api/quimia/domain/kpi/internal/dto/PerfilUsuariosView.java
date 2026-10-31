package com.api.quimia.domain.kpi.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Quantos usuários distintos consultaram produtos da empresa, por nível de acesso e estado. Sem nome nem e-mail.")
public record PerfilUsuariosView(
        @Schema(description = "Nível de acesso do usuário.")
        String nivelAcesso,
        @Schema(description = "UF do usuário; `NI` quando não informada.", example = "SP")
        String estado,
        @Schema(description = "Usuários distintos.", example = "5")
        long totalUsuarios) {}
