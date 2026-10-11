package com.api.quimia.domain.catalog.internal.persistence;

import com.api.quimia.domain.catalog.CatalogEmpresaGateway;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implementação provisória do esqueleto da Fase 2.5: só existe para o contexto subir enquanto o módulo
 * `catalog` não entrega a implementação real. Quem implementa `CatalogEmpresaGateway` apaga esta classe no mesmo commit.
 */
@Component
class CatalogEmpresaGatewayPendente implements CatalogEmpresaGateway {
    @Override
    public List<Integer> produtoIdsAtivos(Integer empresaId) {
        throw naoImplementado();
    }

    @Override
    public Optional<ProdutoView> produtoMaisRecente(Integer empresaId) {
        throw naoImplementado();
    }

    private static UnsupportedOperationException naoImplementado() {
        return new UnsupportedOperationException("CatalogEmpresaGateway ainda não foi implementado");
    }
}
