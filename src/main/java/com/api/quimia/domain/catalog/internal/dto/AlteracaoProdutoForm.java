package com.api.quimia.domain.catalog.internal.dto;

import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * "Alterar produto" (multipart/form-data): campo ausente ou vazio mantém o valor atual. `fds` (PDF) substitui a FDS
 * ativa do produto.
 */
public record AlteracaoProdutoForm(
        @Size(max = 255) String nome,
        @Size(max = 50) String tipo,
        @Size(max = 4000) String descricao,
        @Size(max = 255) String codBarras,
        MultipartFile imagem,
        MultipartFile fds) {}
