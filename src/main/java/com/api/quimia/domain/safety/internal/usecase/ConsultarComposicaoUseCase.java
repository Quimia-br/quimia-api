package com.api.quimia.domain.safety.internal.usecase;

import com.api.quimia.domain.catalog.CatalogGateway;
import com.api.quimia.domain.safety.SafetyGateway;
import com.api.quimia.domain.safety.dto.ComposicaoView;
import java.util.List;
import org.springframework.stereotype.Service;

/** Composição de um produto do catálogo; produto inexistente ou oculto no app responde 404. */
@Service
public class ConsultarComposicaoUseCase {
    private final CatalogGateway catalogo;
    private final SafetyGateway seguranca;

    public ConsultarComposicaoUseCase(CatalogGateway catalogo, SafetyGateway seguranca) {
        this.catalogo = catalogo;
        this.seguranca = seguranca;
    }

    public List<ComposicaoView> execute(Integer produtoId) {
        if (catalogo.visiveis(List.of(produtoId)).isEmpty()) {
            throw new SafetyException("produto_nao_encontrado", 404);
        }
        return seguranca.composicao(produtoId);
    }
}
