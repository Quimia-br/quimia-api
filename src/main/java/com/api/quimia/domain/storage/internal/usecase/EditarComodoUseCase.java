package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.storage.internal.dto.ComodoEdicaoRequest;
import com.api.quimia.domain.storage.internal.dto.ComodoView;
import com.api.quimia.domain.storage.internal.model.Comodo;
import com.api.quimia.domain.storage.internal.model.IconeDeComodo;
import com.api.quimia.domain.storage.internal.persistence.ComodoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Renomeia o cômodo e/ou troca o ícone. Os produtos não mudam. */
@Service
public class EditarComodoUseCase {
    private final ComodoRepository comodos;
    private final MontarComodoView montar;

    public EditarComodoUseCase(ComodoRepository comodos, MontarComodoView montar) {
        this.comodos = comodos;
        this.montar = montar;
    }

    @Transactional
    public ComodoView execute(UUID usuarioId, Integer comodoId, ComodoEdicaoRequest request) {
        Comodo comodo = comodos.findByIdAndUsuarioId(comodoId, usuarioId)
                .orElseThrow(() -> new StorageException("comodo_nao_encontrado", 404));
        if (request.icone() != null && !IconeDeComodo.existe(request.icone())) {
            throw new StorageException("icone_invalido", 422);
        }
        if (request.nome() != null) {
            comodo.setNome(nomeLivre(usuarioId, comodoId, request.nome()));
        }
        if (request.icone() != null) {
            comodo.setIcone(request.icone());
        }
        return montar.view(comodo);
    }

    /** Mesma regra da criação: único por usuário sem acento nem caixa; o próprio cômodo não conta. */
    private String nomeLivre(UUID usuarioId, Integer comodoId, String pedido) {
        String nome = NomeDeComodo.normalizar(pedido);
        comodos.bloquearNome(NomeDeComodo.chaveDeLock(usuarioId, nome));
        String chave = NomeDeComodo.chave(nome);
        boolean repetido = comodos.findByUsuarioIdOrderByIdAsc(usuarioId).stream()
                .filter(outro -> !outro.getId().equals(comodoId))
                .anyMatch(outro -> NomeDeComodo.chave(outro.getNome()).equals(chave));
        if (repetido) {
            throw new StorageException("comodo_ja_existe", 409);
        }
        return nome;
    }
}
