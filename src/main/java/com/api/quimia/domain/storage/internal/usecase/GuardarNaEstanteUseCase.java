package com.api.quimia.domain.storage.internal.usecase;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.catalog.CatalogGateway;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.storage.internal.dto.ItemGuardado;
import com.api.quimia.domain.storage.internal.model.Estante;
import com.api.quimia.domain.storage.internal.persistence.EstanteProdutoRepository;
import com.api.quimia.domain.storage.internal.persistence.EstanteRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Guardar" e "Guardar todos": cria a estante padrão se preciso e informa, por produto, se foi
 * adicionado, se já estava na estante ou se não existe (ou está oculto) no catálogo.
 */
@Service
public class GuardarNaEstanteUseCase {
    static final String ADICIONADO = "adicionado";
    static final String JA_NA_ESTANTE = "ja_na_estante";
    static final String NAO_ENCONTRADO = "nao_encontrado";

    private final EstanteRepository estantes;
    private final EstanteProdutoRepository itens;
    private final CatalogGateway catalogo;
    private final AccountGateway contas;

    public GuardarNaEstanteUseCase(
            EstanteRepository estantes,
            EstanteProdutoRepository itens,
            CatalogGateway catalogo,
            AccountGateway contas) {
        this.estantes = estantes;
        this.itens = itens;
        this.catalogo = catalogo;
        this.contas = contas;
    }

    @Transactional
    public List<ItemGuardado> execute(UUID usuarioId, List<Integer> produtoIds) {
        if (contas.findById(usuarioId).isEmpty()) {
            throw new StorageException("usuario_nao_encontrado", 404);
        }
        LinkedHashSet<Integer> pedidos = new LinkedHashSet<>(produtoIds);
        Map<Integer, String> visiveis = catalogo.visiveis(pedidos).stream()
                .collect(Collectors.toMap(ProdutoView::id, ProdutoView::nome));
        Estante estante = estanteDo(usuarioId);
        return pedidos.stream()
                .map(id -> guardar(estante, id, visiveis.get(id)))
                .toList();
    }

    private Estante estanteDo(UUID usuarioId) {
        estantes.criarSeNaoExistir(usuarioId, Estante.NOME_PADRAO);
        return estantes.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new IllegalStateException("estante not found after insert for " + usuarioId));
    }

    private ItemGuardado guardar(Estante estante, Integer produtoId, String nome) {
        if (nome == null) {
            return new ItemGuardado(produtoId, null, NAO_ENCONTRADO);
        }
        boolean adicionado = itens.guardar(produtoId, estante.getUsuarioId(), estante.getId()) == 1;
        return new ItemGuardado(produtoId, nome, adicionado ? ADICIONADO : JA_NA_ESTANTE);
    }
}
