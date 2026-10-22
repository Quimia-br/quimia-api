package com.api.quimia.domain.safety.dto;

import java.time.LocalDate;

/**
 * Arquivo da FDS ativa de um produto (download no detalhe do produto do portal). `url` e `nomeArquivo` são nulos
 * quando a FDS não veio de um PDF enviado pelo portal.
 */
public record FdsDocumentoView(
        Integer produtoId, String url, String nomeArquivo, String versao, LocalDate dataAtualizacao) {}
