package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Campos do formulário "Cadastrar novo produto" (multipart/form-data). Não há marca: a API usa
 * o nome da empresa. A imagem (JPEG, PNG ou WebP, até 5 MB) é opcional. `fds` é o PDF da Ficha de Dados de
 * Segurança (até 10 MB): obrigatório para produto novo quando `quimia.fds.obrigatoria` está ligada.
 */
@Schema(description = "Formulário multipart do cadastro de produto.")
public record CadastroProdutoForm(
        @Schema(description = "Nome, de 2 a 255 caracteres.", example = "Água Sanitária Limpa Bem")
        @NotBlank @Size(min = 2, max = 255) String nome,
        @Schema(description = "Tipo do produto.", example = "alvejante")
        @NotBlank @Size(max = 50) String tipo,
        @Schema(description = "Descrição, até 4000 caracteres.", example = "Alvejante.")
        @Size(max = 4000) String descricao,
        @Schema(description = "Código de barras (UPC-A ou EAN-13).", example = "7891234567895")
        @Size(max = 255) String codBarras,
        @Schema(description = "Foto do produto: JPEG, PNG ou WebP até 5 MB.")
        MultipartFile imagem,
        @Schema(description = "PDF da Ficha de Dados de Segurança, até 10 MB.")
        MultipartFile fds) {}
