package com.api.quimia.domain.recommendation.internal.usecase;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.recommendation.internal.dto.MisturaView;
import com.api.quimia.domain.recommendation.internal.model.HistoricoMatch;
import com.api.quimia.domain.recommendation.internal.persistence.HistoricoMatchRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarMisturasUseCase {
    public static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 50;

    private final HistoricoMatchRepository historico;
    private final MontarMisturaView montar;

    public ListarMisturasUseCase(HistoricoMatchRepository historico, MontarMisturaView montar) {
        this.historico = historico;
        this.montar = montar;
    }

    @Transactional(readOnly = true)
    public Pagina<MisturaView> execute(UUID usuarioId, Integer produtoId, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO || (long) pagina * tamanho > Integer.MAX_VALUE) {
            throw new RecommendationException("paginacao_invalida", 400);
        }
        PageRequest pedido = PageRequest.of(pagina, tamanho, Sort.by(Sort.Order.desc("dataConsulta"), Sort.Order.desc("id")));
        Page<HistoricoMatch> resultado = produtoId == null
                ? historico.findByUsuarioId(usuarioId, pedido)
                : historico.doUsuarioComProduto(usuarioId, produtoId, pedido);
        return new Pagina<>(
                montar.views(resultado.getContent()),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }
}
