package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.storage.internal.dto.ComodoResumo;
import com.api.quimia.domain.storage.internal.model.Comodo;
import com.api.quimia.domain.storage.internal.persistence.ComodoRepository;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lista paginada, em memória: um usuário tem poucos cômodos. */
@Service
public class ListarComodosUseCase {
    public static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 50;
    private static final int TERMO_MAXIMO = 100;

    private final ComodoRepository comodos;
    private final EstanteProdutoRepository itens;
    private final MontarComodoView montar;

    public ListarComodosUseCase(ComodoRepository comodos, EstanteProdutoRepository itens, MontarComodoView montar) {
        this.comodos = comodos;
        this.itens = itens;
        this.montar = montar;
    }

    @Transactional(readOnly = true)
    public Pagina<ComodoResumo> execute(UUID usuarioId, String termo, String ordem, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO || (long) pagina * tamanho > Integer.MAX_VALUE) {
            throw new StorageException("paginacao_invalida", 400);
        }
        if (termo != null && termo.length() > TERMO_MAXIMO) {
            throw new StorageException("termo_muito_longo", 400);
        }
        OrdemDaEstante.validarDosComodos(ordem);
        List<Comodo> filtrados = comodos.findByUsuarioIdOrderByIdAsc(usuarioId).stream()
                .filter(comodo -> TextoBusca.contem(comodo.getNome(), termo))
                .sorted(comparador(ordem))
                .toList();
        int inicio = (int) Math.min((long) pagina * tamanho, filtrados.size());
        int fim = Math.min(inicio + tamanho, filtrados.size());
        Map<Integer, List<Integer>> produtosPorComodo = new HashMap<>();
        for (Object[] linha : itens.produtosPorComodo(usuarioId)) {
            produtosPorComodo.computeIfAbsent((Integer) linha[0], id -> new ArrayList<>()).add((Integer) linha[1]);
        }
        List<ComodoResumo> itensDaPagina = filtrados.subList(inicio, fim).stream()
                .map(comodo -> montar.resumo(comodo, produtosPorComodo.getOrDefault(comodo.getId(), List.of())))
                .toList();
        int totalPaginas = (int) Math.ceil(filtrados.size() / (double) tamanho);
        return new Pagina<>(itensDaPagina, pagina, tamanho, filtrados.size(), totalPaginas);
    }

    private static Comparator<Comodo> comparador(String ordem) {
        if (OrdemDaEstante.RECENTES.equals(ordem)) {
            return Comparator.comparing(Comodo::getId).reversed();
        }
        if (OrdemDaEstante.ANTIGOS.equals(ordem)) {
            return Comparator.comparing(Comodo::getId);
        }
        return Comparator.comparing((Comodo comodo) -> NomeDeComodo.chave(comodo.getNome()))
                .thenComparing(Comodo::getId);
    }
}
