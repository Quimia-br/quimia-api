package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.catalog.internal.model.TipoProduto;
import org.springframework.stereotype.Component;

/** Regras comuns às operações do portal: empresa existente e ativa, e tipo dentro do CHECK. */
@Component
public class EmpresaDoPortal {
    private final AccountGateway contas;

    public EmpresaDoPortal(AccountGateway contas) {
        this.contas = contas;
    }

    /** O access token vale 15 min: uma empresa desativada nesse intervalo não pode mais operar. */
    public EmpresaView exigirAtiva(Integer empresaId) {
        EmpresaView empresa = contas.findEmpresaById(empresaId)
                .orElseThrow(() -> new CatalogException("empresa_nao_encontrada", 404));
        if (!empresa.ativo()) {
            throw new CatalogException("empresa_inativa", 403);
        }
        return empresa;
    }

    static String tipoValido(String tipo) {
        String valor = tipo.trim();
        if (!TipoProduto.existe(valor)) {
            throw new CatalogException("tipo_invalido", 400);
        }
        return valor;
    }
}
