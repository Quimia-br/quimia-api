package com.api.quimia.domain.safety.internal.usecase;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.catalog.CatalogEmpresaGateway;
import com.api.quimia.domain.safety.SafetyGateway;
import com.api.quimia.domain.safety.dto.FdsDocumentoView;
import org.springframework.stereotype.Service;

/**
 * Arquivo da FDS para o detalhe do produto no portal ("Ficha de dados de segurança", com download). Só a empresa
 * com vínculo ativo com o produto e ativa no momento enxerga; produto de outra empresa é 404, sem revelar que existe.
 */
@Service
public class ConsultarFdsDaEmpresaUseCase {
    private final AccountGateway contas;
    private final CatalogEmpresaGateway produtosDaEmpresa;
    private final SafetyGateway seguranca;

    public ConsultarFdsDaEmpresaUseCase(
            AccountGateway contas, CatalogEmpresaGateway produtosDaEmpresa, SafetyGateway seguranca) {
        this.contas = contas;
        this.produtosDaEmpresa = produtosDaEmpresa;
        this.seguranca = seguranca;
    }

    public FdsDocumentoView execute(Integer empresaId, Integer produtoId) {
        EmpresaView empresa = contas.findEmpresaById(empresaId)
                .orElseThrow(() -> new SafetyException("empresa_nao_encontrada", 404));
        if (!empresa.ativo()) {
            throw new SafetyException("empresa_inativa", 403);
        }
        if (!produtosDaEmpresa.produtoIdsAtivos(empresaId).contains(produtoId)) {
            throw new SafetyException("produto_nao_encontrado", 404);
        }
        return seguranca.documentoDaFds(produtoId).orElseThrow(() -> new SafetyException("fds_nao_encontrada", 404));
    }
}
