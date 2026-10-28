package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Formulário da análise da FDS (multipart/form-data). `fds` é o PDF (até 10 MB). `analiseAnterior` é o `analiseId` da
 * FDS que o PDF novo substitui; o PDF dela é apagado quando a nova análise dá certo.
 */
@Schema(description = "PDF da FDS a analisar.")
public record AnaliseDeFdsForm(
        @Schema(description = "PDF da Ficha de Dados de Segurança, até 10 MB.")
        MultipartFile fds,
        @Schema(description = "`analiseId` da FDS que o PDF novo substitui; o PDF dela é apagado quando a nova "
                + "análise dá certo.")
        @Size(max = 262144) String analiseAnterior) {}
