package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.internal.dto.PaginaResponse;
import com.api.quimia.domain.catalog.internal.dto.ProdutoResponse;
import com.api.quimia.domain.catalog.internal.model.Produto;
import com.api.quimia.domain.catalog.internal.model.TipoProduto;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarProdutosUseCase {
    public static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 50;
    private static final int TERMO_MAXIMO = 100;
    private static final char ESCAPE = '!';

    private final ProdutoRepository produtos;

    public ListarProdutosUseCase(ProdutoRepository produtos) {
        this.produtos = produtos;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ProdutoResponse> execute(String termo, String tipo, String codBarras, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new CatalogException("paginacao_invalida", 400);
        }
        if (tipo != null && !TipoProduto.existe(tipo)) {
            throw new CatalogException("tipo_invalido", 400);
        }
        if (termo != null && termo.length() > TERMO_MAXIMO) {
            throw new CatalogException("termo_muito_longo", 400);
        }
        Page<Produto> page = produtos.buscarVisiveis(
                termoLike(termo),
                tipo,
                blankToNull(codBarras),
                PageRequest.of(pagina, tamanho, Sort.by("nome").and(Sort.by("id"))));
        return new PaginaResponse<>(
                page.getContent().stream().map(ProdutoViews::of).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /** Minúsculas, sem acento e com curingas escapados; o banco compara com lower(unaccent(coluna)). */
    private static String termoLike(String termo) {
        String value = blankToNull(termo);
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String escaped = normalized
                .replace(String.valueOf(ESCAPE), "" + ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
