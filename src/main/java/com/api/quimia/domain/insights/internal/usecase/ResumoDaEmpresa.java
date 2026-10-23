package com.api.quimia.domain.insights.internal.usecase;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.catalog.CatalogEmpresaGateway;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.insights.internal.dto.ResumoEmpresaView;
import com.api.quimia.domain.insights.internal.dto.ResumoMisturas;
import com.api.quimia.domain.recommendation.MisturaGateway;
import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import com.api.quimia.domain.storage.EstanteGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/** Reúne os agregados dos três módulos. Só agregados: nunca identifica usuários. */
@Component
class ResumoDaEmpresa {
    private static final double CASAS_DECIMAIS = 10_000.0;

    private final AccountGateway contas;
    private final CatalogEmpresaGateway catalogo;
    private final EstanteGateway estante;
    private final MisturaGateway misturas;

    ResumoDaEmpresa(AccountGateway contas, CatalogEmpresaGateway catalogo, EstanteGateway estante, MisturaGateway misturas) {
        this.contas = contas;
        this.catalogo = catalogo;
        this.estante = estante;
        this.misturas = misturas;
    }

    ResumoEmpresaView carregar(Integer empresaId) {
        EmpresaView empresa = contas.findEmpresaById(empresaId)
                .orElseThrow(() -> new InsightsException("empresa_nao_encontrada", 404));
        if (!empresa.ativo()) {
            throw new InsightsException("empresa_inativa", 403);
        }
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
