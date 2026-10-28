package com.api.quimia.domain.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Página de resultados; `pagina` começa em 0. */
@Schema(description = "Página de resultados.")
public record Pagina<T>(
        @Schema(description = "Itens da página.")
        List<T> itens,
        @Schema(description = "Página devolvida, a partir de 0.", example = "0")
        int pagina,
        @Schema(description = "Tamanho da página pedido.", example = "20")
        int tamanho,
        @Schema(description = "Total de itens em todas as páginas.", example = "57")
        long totalItens,
        @Schema(description = "Total de páginas.", example = "3")
        int totalPaginas) {
    public static <T> Pagina<T> vazia(int pagina, int tamanho) {
        return new Pagina<>(List.of(), pagina, tamanho, 0, 0);
    }
}
