package com.api.quimia.domain.insights.internal.usecase;

import com.api.quimia.domain.catalog.CatalogEmpresaGateway;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.insights.internal.config.CacheDosInsights;
import com.api.quimia.domain.insights.internal.dto.ResumoEmpresaView;
import com.api.quimia.domain.insights.internal.dto.ResumoMisturas;
import com.api.quimia.domain.recommendation.MisturaGateway;
import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import com.api.quimia.domain.storage.EstanteGateway;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Os agregados do painel, no cache por alguns minutos: são contagens sobre estantes e misturas de muitos usuários, e
 * um atraso curto não muda a leitura do painel. A checagem da empresa ativa fica fora, em {@link ResumoDaEmpresa}.
 */
@Component
public class AgregadosDaEmpresa {
    private static final double CASAS_DECIMAIS = 10_000.0;

    private final CatalogEmpresaGateway catalogo;
    private final EstanteGateway estante;
    private final MisturaGateway misturas;

    AgregadosDaEmpresa(CatalogEmpresaGateway catalogo, EstanteGateway estante, MisturaGateway misturas) {
        this.catalogo = catalogo;
        this.estante = estante;
        this.misturas = misturas;
    }

    @Cacheable(CacheDosInsights.RESUMO_DA_EMPRESA)
    public ResumoEmpresaView calcular(Integer empresaId) {
        List<Integer> produtoIds = catalogo.produtoIdsAtivos(empresaId);
        ResumoMisturasView resumo = misturas.resumoDosProdutos(produtoIds);
        ProdutoView destaque = catalogo.produtoMaisRecente(empresaId).orElse(null);
        return new ResumoEmpresaView(
                estante.contarGuardados(produtoIds),
                new ResumoMisturas(
                        resumo.total(), resumo.compativeis(), resumo.incompativeis(), resumo.naoAvaliadas(), taxa(resumo)),
                destaque);
    }

    private static Double taxa(ResumoMisturasView resumo) {
        long avaliadas = resumo.compativeis() + resumo.incompativeis();
        if (avaliadas == 0) {
            return null;
        }
        return Math.round(resumo.compativeis() * CASAS_DECIMAIS / avaliadas) / CASAS_DECIMAIS;
    }
}
