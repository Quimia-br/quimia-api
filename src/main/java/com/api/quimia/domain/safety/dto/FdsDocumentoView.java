package com.api.quimia.domain.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Arquivo da FDS ativa de um produto (download no detalhe do produto do portal). `url` e `nomeArquivo` são nulos
 * quando a FDS não veio de um PDF enviado pelo portal.
 */
@Schema(description = "PDF da FDS ativa do produto.")
public record FdsDocumentoView(
        @Schema(description = "Id do produto.", example = "42")
        Integer produtoId,
        @Schema(
                description = "Link https do PDF. Nulo quando a FDS não tem PDF do portal.",
                example = "https://res.cloudinary.com/quimia/raw/upload/fds/12/uuid/ficha.pdf")
        String url,
        @Schema(description = "Nome do arquivo para o download.", example = "ficha.pdf")
        String nomeArquivo,
        @Schema(description = "Versão da FDS.", example = "3")
        String versao,
        @Schema(description = "Data da FDS.", example = "2026-01-15")
        LocalDate dataAtualizacao) {}
