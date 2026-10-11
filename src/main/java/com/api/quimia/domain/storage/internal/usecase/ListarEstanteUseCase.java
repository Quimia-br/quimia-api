package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.catalog.CatalogGateway;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Minha estante": inclui produtos que ficaram ocultos no catálogo depois de guardados. */
@Service
public class ListarEstanteUseCase {
    public static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 50;
    private static final int TERMO_MAXIMO = 100;

    private final EstanteProdutoRepository itens;
    private final CatalogGateway catalogo;

    public ListarEstanteUseCase(EstanteProdutoRepository itens, CatalogGateway catalogo) {
        this.itens = itens;
        this.catalogo = catalogo;
    }

    @Transactional(readOnly = true)
    public Pagina<ProdutoView> execute(UUID usuarioId, String termo, int pagina, int tamanho) {
        // O offset da consulta é um int: páginas além dele viravam 500 no Spring Data.
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO || (long) pagina * tamanho > Integer.MAX_VALUE) {
            throw new StorageException("paginacao_invalida", 400);
        }
        if (termo != null && termo.length() > TERMO_MAXIMO) {
            throw new StorageException("termo_muito_longo", 400);
        }
        return catalogo.listarPorIds(itens.produtoIds(usuarioId), termo, pagina, tamanho);
    }
}
