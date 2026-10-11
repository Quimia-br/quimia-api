package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.catalog.CatalogGateway;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.safety.SafetyGateway;
import com.api.quimia.domain.safety.dto.SubstanciaNaEstanteView;
import com.api.quimia.domain.storage.internal.dto.ProdutoDaSubstancia;
import com.api.quimia.domain.storage.internal.dto.SubstanciaDaEstante;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Substâncias dos produtos da estante, cada uma com os produtos do usuário em que aparece. */
@Service
public class ListarSubstanciasDaEstanteUseCase {
    private final EstanteProdutoRepository itens;
    private final SafetyGateway seguranca;
    private final CatalogGateway catalogo;

    public ListarSubstanciasDaEstanteUseCase(
            EstanteProdutoRepository itens, SafetyGateway seguranca, CatalogGateway catalogo) {
        this.itens = itens;
        this.seguranca = seguranca;
        this.catalogo = catalogo;
    }

    private static final int TERMO_MAXIMO = 100;

    @Transactional(readOnly = true)
    public List<SubstanciaDaEstante> execute(UUID usuarioId, String termo, String ordem) {
        OrdemDaEstante.validar(ordem);
        if (termo != null && termo.length() > TERMO_MAXIMO) {
            throw new StorageException("termo_muito_longo", 400);
        }
        List<Integer> produtoIds = itens.produtoIds(usuarioId);
        List<SubstanciaNaEstanteView> substancias = seguranca.substanciasDosProdutos(produtoIds);
        if (substancias.isEmpty()) {
            return List.of();
        }
        // Os produtos já estão na estante; os ocultos do catálogo continuam valendo para a segurança.
        Map<Integer, ProdutoView> produtos = catalogo
                .listarPorIds(produtoIds, null, 0, produtoIds.size())
                .itens()
                .stream()
                .collect(Collectors.toMap(ProdutoView::id, Function.identity()));
        return substancias.stream()
                .filter(substancia -> TextoBusca.contem(substancia.nome(), termo))
                .map(substancia -> new SubstanciaDaEstante(
                        substancia.id(),
                        substancia.nome(),
                        substancia.casNumero(),
                        substancia.descricao(),
                        substancia.classes(),
                        substancia.produtoIds().stream()
                                .map(produtos::get)
                                .map(produto -> new ProdutoDaSubstancia(produto.id(), produto.nome()))
                                .toList()))
                .toList();
    }
}
