package com.api.quimia.domain.catalog.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Campos do formulário "Cadastrar novo produto" (multipart/form-data). Não há marca: a API usa
 * o nome da empresa. A imagem (JPEG, PNG ou WebP, até 5 MB) é opcional.
 */
public record CadastroProdutoForm(
        @NotBlank @Size(min = 2, max = 255) String nome,
        @NotBlank @Size(max = 50) String tipo,
        @Size(max = 4000) String descricao,
        @Size(max = 255) String codBarras,
        MultipartFile imagem) {}
