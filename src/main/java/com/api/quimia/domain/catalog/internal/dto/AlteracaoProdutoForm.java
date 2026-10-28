package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * "Alterar produto" (multipart/form-data): campo ausente ou vazio mantém o valor atual. `fds` (PDF) substitui a FDS
 * ativa do produto.
 */
@Schema(description = "Formulário multipart da alteração: só os campos enviados mudam.")
public record AlteracaoProdutoForm(
        @Schema(description = "Nome, de 2 a 255 caracteres.", example = "Água Sanitária Limpa Bem")
        @Size(max = 255) String nome,
        @Schema(description = "Tipo do produto.", example = "alvejante")
        @Size(max = 50) String tipo,
        @Schema(description = "Descrição, até 4000 caracteres.", example = "Alvejante.")
        @Size(max = 4000) String descricao,
        @Schema(description = "Código de barras (UPC-A ou EAN-13).", example = "7891234567895")
        @Size(max = 255) String codBarras,
        @Schema(description = "Nova foto: JPEG, PNG ou WebP até 5 MB.")
        MultipartFile imagem,
        @Schema(description = "PDF de uma FDS nova, até 10 MB (a anterior é desativada).")
        MultipartFile fds) {}
