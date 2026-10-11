package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.storage.internal.dto.ComodoView;
import com.api.quimia.domain.storage.internal.dto.ProdutosNoComodoRequest;
import com.api.quimia.domain.storage.internal.model.Comodo;
import com.api.quimia.domain.storage.internal.persistence.ComodoRepository;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotente: produto que já está no cômodo não é erro. Não exige o mínimo de 2 (só a criação exige). */
@Service
public class AdicionarProdutosAoComodoUseCase {
    private final ComodoRepository comodos;
    private final EstanteProdutoRepository itens;
    private final MontarComodoView montar;

    public AdicionarProdutosAoComodoUseCase(ComodoRepository comodos, EstanteProdutoRepository itens, MontarComodoView montar) {
        this.comodos = comodos;
        this.itens = itens;
        this.montar = montar;
    }

    @Transactional
    public ComodoView execute(UUID usuarioId, Integer comodoId, ProdutosNoComodoRequest request) {
        Comodo comodo = comodos.findByIdAndUsuarioId(comodoId, usuarioId)
                .orElseThrow(() -> new StorageException("comodo_nao_encontrado", 404));
        List<Integer> pedidos = request.produtoIds().stream().distinct().toList();
        if (!Set.copyOf(itens.produtoIds(usuarioId)).containsAll(pedidos)) {
            throw new StorageException("produto_fora_da_estante", 422);
        }
        Set<Integer> resultado = new HashSet<>(itens.produtoIdsDoComodo(usuarioId, comodoId));
        resultado.addAll(pedidos);
        if (resultado.size() > CriarComodoUseCase.MAXIMO_PRODUTOS) {
            throw new StorageException("comodo_produtos_demais", 422);
        }
        if (!pedidos.isEmpty() && itens.atribuirComodo(usuarioId, pedidos, comodoId) != pedidos.size()) {
            throw new StorageException("produto_em_outro_comodo", 409);
        }
        return montar.view(comodo);
    }
}
