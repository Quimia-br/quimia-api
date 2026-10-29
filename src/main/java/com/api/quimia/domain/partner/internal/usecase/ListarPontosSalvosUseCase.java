package com.api.quimia.domain.partner.internal.usecase;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.partner.internal.dto.PontoSalvoView;
import com.api.quimia.domain.partner.internal.persistence.PontoSalvoRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pontos salvos do usuário, do mais recente ao mais antigo. Pontos desativados depois de salvos não aparecem. */
@Service
public class ListarPontosSalvosUseCase {
    public static final int TAMANHO_PADRAO = 20;

    private final PontoSalvoRepository salvos;

    public ListarPontosSalvosUseCase(PontoSalvoRepository salvos) {
        this.salvos = salvos;
    }

    @Transactional(readOnly = true)
    public Pagina<PontoSalvoView> execute(UUID usuarioId, String tipo, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > PontosSalvos.TAMANHO_MAXIMO
                || (long) pagina * tamanho > Integer.MAX_VALUE) {
            throw new PartnerException("paginacao_invalida", 400);
        }
        PontosSalvos.validarTipo(tipo);
        Page<Object[]> linhas = salvos.doUsuario(usuarioId, tipo, PageRequest.of(pagina, tamanho));
        return new Pagina<>(
                linhas.getContent().stream().map(PontosSalvos::view).toList(),
                pagina,
                tamanho,
                linhas.getTotalElements(),
                linhas.getTotalPages());
    }
}
