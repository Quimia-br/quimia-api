package com.api.quimia.domain.insights.internal.usecase;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.insights.internal.dto.ResumoEmpresaView;
import org.springframework.stereotype.Component;

/**
 * Reúne os agregados dos três módulos. Só agregados: nunca identifica usuários. A empresa é conferida a cada pedido,
 * fora do cache: desativada, para de ver o painel na hora.
 */
@Component
class ResumoDaEmpresa {
    private final AccountGateway contas;
    private final AgregadosDaEmpresa agregados;

    ResumoDaEmpresa(AccountGateway contas, AgregadosDaEmpresa agregados) {
        this.contas = contas;
        this.agregados = agregados;
    }

    ResumoEmpresaView carregar(Integer empresaId) {
        EmpresaView empresa = contas.findEmpresaById(empresaId)
                .orElseThrow(() -> new InsightsException("empresa_nao_encontrada", 404));
        if (!empresa.ativo()) {
            throw new InsightsException("empresa_inativa", 403);
        }
        return agregados.calcular(empresaId);
    }
}
