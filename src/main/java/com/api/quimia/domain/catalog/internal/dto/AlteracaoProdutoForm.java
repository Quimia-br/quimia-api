package com.api.quimia.domain.catalog.internal.dto;

import jakarta.validation.constraints.Size;

/** "Alterar produto" (multipart/form-data): campo ausente ou vazio mantém o valor atual. */
public record AlteracaoProdutoForm(
        @Size(max = 255) String nome,
        @Size(max = 50) String tipo,
        @Size(max = 4000) String descricao,
        @Size(max = 255) String codBarras) {}
