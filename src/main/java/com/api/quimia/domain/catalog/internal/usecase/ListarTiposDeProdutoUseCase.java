package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.internal.model.TipoProduto;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Opções do campo "Tipo" do cadastro de produto. Vêm do mesmo enum que o cadastro e a listagem validam
 * ({@link TipoProduto}, os valores do CHECK de `produto.tipo_produto`), então a lista nunca diverge do que é aceito.
 */
@Service
public class ListarTiposDeProdutoUseCase {
    public List<String> execute() {
        return Arrays.stream(TipoProduto.values()).map(TipoProduto::codigo).toList();
    }
}
