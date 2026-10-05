package com.api.quimia.domain.safety.dto;

import java.time.LocalDate;

/** Texto de `fds.resumo_simplificado`; `resumo`, `versao` e `dataAtualizacao` são nulos sem FDS ativa. */
public record ResumoSimplificadoView(Integer produtoId, String resumo, String versao, LocalDate dataAtualizacao) {}
