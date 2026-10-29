package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.catalog.CatalogGateway;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    public Pagina<ProdutoView> execute(UUID usuarioId, String termo, String ordem, int pagina, int tamanho) {
        // O offset da consulta é um int: páginas além dele viravam 500 no Spring Data.
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO || (long) pagina * tamanho > Integer.MAX_VALUE) {
            throw new StorageException("paginacao_invalida", 400);
        }
        if (termo != null && termo.length() > TERMO_MAXIMO) {
            throw new StorageException("termo_muito_longo", 400);
        }
        OrdemDaEstante.validarDosProdutos(ordem);
        if (OrdemDaEstante.RECENTES.equals(ordem)) {
            return paginarNaOrdem(itens.produtoIdsDosRecentes(usuarioId), termo, pagina, tamanho);
        }
        if (OrdemDaEstante.ANTIGOS.equals(ordem)) {
            return paginarNaOrdem(itens.produtoIdsDosAntigos(usuarioId), termo, pagina, tamanho);
        }
        return catalogo.listarPorIds(itens.produtoIds(usuarioId), termo, pagina, tamanho);
    }

    /** O catálogo filtra pelo termo mas ordena por nome: a ordem por data é refeita aqui. */
    private Pagina<ProdutoView> paginarNaOrdem(List<Integer> ids, String termo, int pagina, int tamanho) {
        if (ids.isEmpty()) {
            return Pagina.vazia(pagina, tamanho);
        }
        Map<Integer, ProdutoView> filtrados = catalogo.listarPorIds(ids, termo, 0, ids.size()).itens().stream()
                .collect(Collectors.toMap(ProdutoView::id, Function.identity()));
        List<ProdutoView> naOrdem = ids.stream().filter(filtrados::containsKey).map(filtrados::get).toList();
        int inicio = (int) Math.min((long) pagina * tamanho, naOrdem.size());
        int fim = Math.min(inicio + tamanho, naOrdem.size());
        int totalPaginas = (naOrdem.size() + tamanho - 1) / tamanho;
        return new Pagina<>(naOrdem.subList(inicio, fim), pagina, tamanho, naOrdem.size(), totalPaginas);
    }
}
