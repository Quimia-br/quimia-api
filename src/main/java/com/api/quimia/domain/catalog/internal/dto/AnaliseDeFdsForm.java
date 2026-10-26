package com.api.quimia.domain.catalog.internal.dto;

import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Formulário da análise da FDS (multipart/form-data). `fds` é o PDF (até 10 MB). `analiseAnterior` é o `analiseId` da
 * FDS que o PDF novo substitui; o PDF dela é apagado quando a nova análise dá certo.
 */
public record AnaliseDeFdsForm(MultipartFile fds, @Size(max = 262144) String analiseAnterior) {}
