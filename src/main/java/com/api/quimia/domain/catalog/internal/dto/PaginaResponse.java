package com.api.quimia.domain.catalog.internal.dto;

import java.util.List;

/** `pagina` começa em 0. */
public record PaginaResponse<T>(List<T> itens, int pagina, int tamanho, long totalItens, int totalPaginas) {}
