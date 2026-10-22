package com.api.quimia.domain.storage.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Substância presente na estante.")
public record SubstanciaDaEstante(
        @Schema(description = "Id da substância.", example = "7")
        Integer id,
        @Schema(description = "Nome canônico.", example = "Hipoclorito de sódio")
        String nome,
        @Schema(description = "Número CAS.", example = "7681-52-9")
        String casNumero,
        @Schema(description = "Descrição da substância.", example = "Oxidante.")
        String descricao,
        @Schema(description = "Classes químicas.")
        List<String> classes,
        @Schema(description = "Produtos da estante em que aparece.")
        List<ProdutoDaSubstancia> produtos) {}
