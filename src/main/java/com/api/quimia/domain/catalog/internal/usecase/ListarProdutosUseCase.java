package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.CodigoBarras;
import com.api.quimia.domain.catalog.internal.model.TermoBusca;
import com.api.quimia.domain.catalog.internal.model.TipoProduto;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarProdutosUseCase {
    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 50;
    public static final int TERMO_MAXIMO = 100;

    private final ProdutoRepository produtos;

    public ListarProdutosUseCase(ProdutoRepository produtos) {
        this.produtos = produtos;
    }

    @Transactional(readOnly = true)
    public Pagina<ProdutoView> execute(String termo, String tipo, String codBarras, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO || offsetExcedeLimite(pagina, tamanho)) {
            throw new CatalogException("paginacao_invalida", 400);
        }
        if (tipo != null && !TipoProduto.existe(tipo)) {
            throw new CatalogException("tipo_invalido", 400);
        }
        if (termo != null && termo.length() > TERMO_MAXIMO) {
            throw new CatalogException("termo_muito_longo", 400);
        }
        CamposDaBusca campos = CamposDaBusca.DO_APP;
        String codigo = CodigoBarras.paraFiltroDoApp(codBarras);
        return ProdutoMapper.pagina(produtos.buscarVisiveis(
                TermoBusca.like(termo),
                campos.nome(),
                campos.descricaoEMarca(),
                campos.tipo(),
                CamposDaBusca.tiposDoTermo(termo),
                campos.codBarras(),
                tipo,
                codigo == null,
                CodigoBarras.paraFiltro(codigo),
                PageRequest.of(pagina, tamanho, Sort.by("nome").and(Sort.by("id")))));
    }

    /** O offset da consulta é um int: páginas além dele viravam 500 no Spring Data. */
    static boolean offsetExcedeLimite(int pagina, int tamanho) {
        return (long) pagina * tamanho > Integer.MAX_VALUE;
    }
}
