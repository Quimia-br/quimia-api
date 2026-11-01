package com.api.quimia.domain.recommendation.internal.persistence;

import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import java.util.List;

/** Converte as linhas `(resultado, quantidade)` de `historico_match` no resumo; resultado desconhecido conta como não avaliado. */
public final class ContagemPorResultado {
    private ContagemPorResultado() {}

    public static ResumoMisturasView resumo(List<Object[]> linhas) {
        long compativeis = 0;
        long incompativeis = 0;
        long naoAvaliadas = 0;
        for (Object[] linha : linhas) {
            long quantidade = ((Number) linha[1]).longValue();
            switch ((String) linha[0]) {
                case "compativel" -> compativeis = quantidade;
                case "incompativel" -> incompativeis = quantidade;
                default -> naoAvaliadas += quantidade;
            }
        }
        return new ResumoMisturasView(compativeis + incompativeis + naoAvaliadas, compativeis, incompativeis, naoAvaliadas);
    }
}
