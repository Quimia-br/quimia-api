package com.api.quimia.domain.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Substância.")
public record SubstanciaView(
        @Schema(description = "Id da substância.", example = "7")
        Integer id,
        @Schema(description = "Nome canônico.", example = "Hipoclorito de sódio")
        String nome,
        @Schema(description = "Número CAS.", example = "7681-52-9")
        String casNumero,
        @Schema(description = "Descrição.", example = "Oxidante usado como alvejante.")
        String descricao,
        @Schema(description = "Outros nomes.")
        List<String> sinonimos,
        @Schema(description = "Classes químicas.")
        List<ClasseQuimicaView> classes) {}
