package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.internal.model.TermoBusca;
import com.api.quimia.domain.catalog.internal.model.TipoProduto;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Em quais campos o `q` da listagem do portal é procurado (`fields`, os chips "Campos da busca" do web: Nome, Tipo e
 * Código de barras). Sem `fields`, procura em todos: nome, descrição, marca, tipo e código. O app não tem `fields` e
 * mantém a busca por nome, descrição e marca ({@link #DO_APP}).
 */
record CamposDaBusca(boolean nome, boolean descricaoEMarca, boolean tipo, boolean codBarras) {
    private static final String NOME = "nome";
    private static final String TIPO = "tipo";
    private static final String COD_BARRAS = "codBarras";
    private static final Set<String> CAMPOS_VALIDOS = Set.of(NOME, TIPO, COD_BARRAS);
    /** `IN` não aceita lista vazia: um valor que nenhum tipo gravado tem. */
    private static final Set<String> NENHUM_TIPO = Set.of("");
    /** Busca do app (`GET /produtos`): nome, descrição e marca, sem tipo nem código. */
    static final CamposDaBusca DO_APP = new CamposDaBusca(true, true, false, false);

    /** Elementos em branco (ex.: `fields=nome,`) são ignorados; sem nenhum campo, procura em todos. */
    static CamposDaBusca de(List<String> fields) {
        List<String> pedidos = fields == null
                ? List.of()
                : fields.stream().map(String::trim).filter(campo -> !campo.isEmpty()).toList();
        if (pedidos.isEmpty()) {
            return new CamposDaBusca(true, true, true, true);
        }
        if (!CAMPOS_VALIDOS.containsAll(pedidos)) {
            throw new CatalogException("campo_busca_invalido", 400);
        }
        return new CamposDaBusca(pedidos.contains(NOME), false, pedidos.contains(TIPO), pedidos.contains(COD_BARRAS));
    }

    /**
     * Tipos cujo código escrito por extenso (`limpeza_geral` vira "limpeza geral") contém o termo, sem acento e sem
     * diferenciar caixa. O banco guarda o código, então a comparação com o texto digitado é feita aqui.
     */
    static Set<String> tiposDoTermo(String termo) {
        String procurado = TermoBusca.normalizado(termo);
        if (procurado == null) {
            return NENHUM_TIPO;
        }
        Set<String> tipos = Arrays.stream(TipoProduto.values())
                .map(TipoProduto::codigo)
                .filter(codigo -> codigo.replace('_', ' ').contains(procurado))
                .collect(Collectors.toSet());
        return tipos.isEmpty() ? NENHUM_TIPO : tipos;
    }
}
