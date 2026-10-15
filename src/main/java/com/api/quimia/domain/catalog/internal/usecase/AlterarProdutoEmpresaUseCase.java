package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.dto.AlteracaoProdutoForm;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.Produto;
import com.api.quimia.domain.catalog.internal.persistence.EmpresaProdutoRepository;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O produto é compartilhado (estantes e outras empresas), então só pode ser alterado pela empresa
 * que tem o único vínculo ativo com ele.
 */
@Service
public class AlterarProdutoEmpresaUseCase {
    private final EmpresaDoPortal portal;
    private final ProdutoRepository produtos;
    private final EmpresaProdutoRepository vinculos;
    private final ImagemDoProduto imagens;

    public AlterarProdutoEmpresaUseCase(
            EmpresaDoPortal portal,
            ProdutoRepository produtos,
            EmpresaProdutoRepository vinculos,
            ImagemDoProduto imagens) {
        this.portal = portal;
        this.produtos = produtos;
        this.vinculos = vinculos;
        this.imagens = imagens;
    }

    @Transactional
    public ProdutoView execute(Integer empresaId, Integer produtoId, AlteracaoProdutoForm form) {
        portal.exigirAtiva(empresaId);
        byte[] imagem = imagens.ler(form.imagem());
        Produto produto = produtos.findDaEmpresa(produtoId, empresaId)
                .orElseThrow(CatalogException::produtoNaoEncontrado);
        if (vinculos.contarAtivos(produtoId) > 1) {
            throw new CatalogException("produto_compartilhado", 409);
        }
        String nome = CadastrarProdutoEmpresaUseCase.textoOuNulo(form.nome());
        if (nome != null) {
            if (nome.length() < CadastrarProdutoEmpresaUseCase.NOME_MINIMO) {
                throw new CatalogException("nome_invalido", 400);
            }
            produto.setNome(nome);
        }
        if (CadastrarProdutoEmpresaUseCase.textoOuNulo(form.tipo()) != null) {
            produto.setTipo(EmpresaDoPortal.tipoValido(form.tipo()));
        }
        String descricao = CadastrarProdutoEmpresaUseCase.textoOuNulo(form.descricao());
        if (descricao != null) {
            produto.setDescricao(descricao);
        }
        String codBarras = CatalogException.codigoBarras(form.codBarras());
        if (codBarras != null && !codBarras.equals(produto.getCodBarras())) {
            boolean ocupado = CadastrarProdutoEmpresaUseCase.equivalente(produtos, codBarras)
                    .filter(outro -> !outro.getId().equals(produto.getId()))
                    .isPresent();
            if (ocupado) {
                throw new CatalogException("cod_barras_em_uso", 409);
            }
            produto.setCodBarras(codBarras);
        }
        if (imagem != null) {
            produto.setFotoUrl(imagens.enviar(imagem, produto.getFotoUrl()));
        }
        return ProdutoMapper.view(produto);
    }
}
