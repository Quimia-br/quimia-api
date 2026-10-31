package com.api.quimia.domain.kpi.internal.usecase;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.kpi.internal.dto.ConsultasDiariasView;
import com.api.quimia.domain.kpi.internal.dto.DauDiarioView;
import com.api.quimia.domain.kpi.internal.dto.PerfilUsuariosView;
import com.api.quimia.domain.kpi.internal.dto.TopProdutoView;
import com.api.quimia.domain.kpi.internal.persistence.KpiEmpresaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KPIs da empresa logada. A empresa é conferida a cada pedido: desativada, para de ver os gráficos na hora. Listas
 * vazias são resposta válida (empresa sem consultas), nunca erro.
 */
@Service
public class ConsultarKpisUseCase {
    public static final int LIMITE_PADRAO = 10;
    public static final int LIMITE_MINIMO = 1;
    public static final int LIMITE_MAXIMO = 50;

    private final AccountGateway contas;
    private final KpiEmpresaRepository kpis;

    public ConsultarKpisUseCase(AccountGateway contas, KpiEmpresaRepository kpis) {
        this.contas = contas;
        this.kpis = kpis;
    }

    @Transactional(readOnly = true)
    public List<DauDiarioView> dau(Integer empresaId) {
        conferirEmpresa(empresaId);
        return kpis.dau(empresaId);
    }

    @Transactional(readOnly = true)
    public List<ConsultasDiariasView> recomendacoes(Integer empresaId) {
        conferirEmpresa(empresaId);
        return kpis.consultasPorDia(empresaId);
    }

    @Transactional(readOnly = true)
    public List<TopProdutoView> topProdutos(Integer empresaId, int limite) {
        if (limite < LIMITE_MINIMO || limite > LIMITE_MAXIMO) {
            throw new KpiException("limite_invalido", 400);
        }
        conferirEmpresa(empresaId);
        return kpis.topProdutos(empresaId, limite);
    }

    @Transactional(readOnly = true)
    public List<PerfilUsuariosView> perfilUsuarios(Integer empresaId) {
        conferirEmpresa(empresaId);
        return kpis.perfilUsuarios(empresaId);
    }

    private void conferirEmpresa(Integer empresaId) {
        EmpresaView empresa = contas.findEmpresaById(empresaId)
                .orElseThrow(() -> new KpiException("empresa_nao_encontrada", 404));
        if (!empresa.ativo()) {
            throw new KpiException("empresa_inativa", 403);
        }
    }
}
