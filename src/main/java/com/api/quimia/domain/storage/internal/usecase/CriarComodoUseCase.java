package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.storage.internal.dto.ComodoRequest;
import com.api.quimia.domain.storage.internal.dto.ComodoView;
import com.api.quimia.domain.storage.internal.model.Comodo;
import com.api.quimia.domain.storage.internal.persistence.ComodoRepository;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cria o cômodo e liga os produtos da estante a ele, tudo ou nada. */
@Service
public class CriarComodoUseCase {
    static final int MINIMO_PRODUTOS = 2;
    static final int MAXIMO_PRODUTOS = 20;

    private final ComodoRepository comodos;
    private final EstanteProdutoRepository itens;
    private final MontarComodoView montar;

    public CriarComodoUseCase(ComodoRepository comodos, EstanteProdutoRepository itens, MontarComodoView montar) {
        this.comodos = comodos;
        this.itens = itens;
        this.montar = montar;
    }

    @Transactional
    public ComodoView execute(UUID usuarioId, ComodoRequest request) {
        String nome = NomeDeComodo.normalizar(request.nome());
        List<Integer> produtoIds = request.produtoIds().stream().distinct().toList();
        if (produtoIds.size() < MINIMO_PRODUTOS) {
            throw new StorageException("comodo_produtos_insuficientes", 422);
        }
        if (produtoIds.size() > MAXIMO_PRODUTOS) {
            throw new StorageException("comodo_produtos_demais", 422);
        }
        comodos.bloquearNome(NomeDeComodo.chaveDeLock(usuarioId, nome));
        String chave = NomeDeComodo.chave(nome);
        boolean repetido = comodos.findByUsuarioIdOrderByIdAsc(usuarioId).stream()
                .anyMatch(existente -> NomeDeComodo.chave(existente.getNome()).equals(chave));
        if (repetido) {
            throw new StorageException("comodo_ja_existe", 409);
        }
        Set<Integer> naEstante = Set.copyOf(itens.produtoIds(usuarioId));
        if (!naEstante.containsAll(produtoIds)) {
            throw new StorageException("produto_fora_da_estante", 422);
        }
        Comodo comodo = new Comodo();
        comodo.setUsuarioId(usuarioId);
        comodo.setNome(nome);
        comodo.setIcone(request.icone());
        comodo = comodos.save(comodo);
        if (itens.atribuirComodo(usuarioId, produtoIds, comodo.getId()) != produtoIds.size()) {
            throw new StorageException("produto_em_outro_comodo", 409);
        }
        return montar.view(comodo);
    }
}
