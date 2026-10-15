package com.api.quimia.domain.catalog.dto;

import java.util.List;

/** Página de resultados; `pagina` começa em 0. */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long totalItens, int totalPaginas) {
    public static <T> Pagina<T> vazia(int pagina, int tamanho) {
        return new Pagina<>(List.of(), pagina, tamanho, 0, 0);
    }
}
