package com.api.quimia.domain.catalog.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Campos do formulário "Cadastrar novo produto" (multipart/form-data). Não há marca: a API usa
 * o nome da empresa. A imagem (JPEG, PNG ou WebP, até 5 MB) é opcional. `fds` é o PDF da Ficha de Dados de
 * Segurança (até 10 MB): obrigatório para produto novo quando `quimia.fds.obrigatoria` está ligada.
 * Em vez do PDF, o portal pode mandar `analiseFds` (a análise já revisada) e as `correcoesFds` da empresa.
 */
public record CadastroProdutoForm(
        @NotBlank @Size(min = 2, max = 255) String nome,
        @NotBlank @Size(max = 50) String tipo,
        @Size(max = 4000) String descricao,
        @Size(max = 255) String codBarras,
        MultipartFile imagem,
        MultipartFile fds,
        @Size(max = 262144) String analiseFds,
        @Size(max = 4000) String correcoesFds) {}
