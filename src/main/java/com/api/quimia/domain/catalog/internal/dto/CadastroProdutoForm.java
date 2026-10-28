package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Campos do formulário "Cadastrar novo produto" (multipart/form-data). Não há marca: a API usa
 * o nome da empresa. A imagem (JPEG, PNG ou WebP, até 5 MB) é opcional. `fds` é o PDF da Ficha de Dados de
 * Segurança (até 10 MB): obrigatório para produto novo quando `quimia.fds.obrigatoria` está ligada.
 * Em vez do PDF, o portal pode mandar `analiseFds` (a análise já revisada) e as `correcoesFds` da empresa.
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
        MultipartFile fds,
        @Schema(description = "`analiseId` de uma análise já revisada (`POST /produtos/fds/analises`), no lugar do PDF. "
                + "Não vale junto com `fds`.")
        @Size(max = 262144) String analiseFds,
        @Schema(description = "Correções da revisão, só com `analiseFds`: lista JSON de `{\"indice\": 0, "
                + "\"unidadeConcentracao\": \"%\"}` (unidade do composto que veio sem ela).",
                example = "[{\"indice\":0,\"unidadeConcentracao\":\"%\"}]")
        @Size(max = 4000) String correcoesFds) {}
