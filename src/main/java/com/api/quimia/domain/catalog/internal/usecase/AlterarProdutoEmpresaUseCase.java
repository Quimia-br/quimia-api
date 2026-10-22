package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.dto.AlteracaoProdutoForm;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.Produto;
import com.api.quimia.domain.catalog.internal.persistence.EmpresaProdutoRepository;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O produto é compartilhado (estantes e outras empresas), então só pode ser alterado pela empresa
 * que tem o único vínculo ativo com ele.
 *
 * Uma FDS nova (PDF) é guardada e passa pelo extrator antes da transação, depois de validar os campos e conferir que
 * o produto pode ser alterado; ela vira a FDS ativa do produto na mesma transação da alteração. Se a alteração
 * falhar, o PDF guardado é apagado.
 */
@Service
public class AlterarProdutoEmpresaUseCase {
    private final EmpresaDoPortal portal;
    private final ProdutoRepository produtos;
    private final EmpresaProdutoRepository vinculos;
    private final ImagemDoProduto imagens;
    private final FdsDoProduto fds;
    private final TransactionTemplate transacao;

    /** Campos do formulário já validados; `null` mantém o valor atual. */
    private record Alteracao(String nome, String tipo, String descricao, String codBarras) {}

    public AlterarProdutoEmpresaUseCase(
            EmpresaDoPortal portal,
            ProdutoRepository produtos,
            EmpresaProdutoRepository vinculos,
            ImagemDoProduto imagens,
            FdsDoProduto fds,
            PlatformTransactionManager transacoes) {
        this.portal = portal;
        this.produtos = produtos;
        this.vinculos = vinculos;
        this.imagens = imagens;
        this.fds = fds;
        this.transacao = new TransactionTemplate(transacoes);
    }

    public ProdutoView execute(Integer empresaId, Integer produtoId, AlteracaoProdutoForm form) {
        portal.exigirAtiva(empresaId);
        Alteracao alteracao = validar(form);
        byte[] imagem = imagens.ler(form.imagem());
        FdsDoProduto.FdsPreparada fdsPreparada = fdsNova(empresaId, produtoId, fds.ler(form.fds()));
        try {
            return transacao.execute(status -> gravar(empresaId, produtoId, alteracao, imagem, fdsPreparada));
        } catch (RuntimeException falha) {
            if (fdsPreparada != null) {
                fds.descartar(fdsPreparada);
            }
            throw falha;
        }
    }

    /** O que não depende do banco é recusado antes de qualquer chamada lenta (extrator) ou escrita. */
    private static Alteracao validar(AlteracaoProdutoForm form) {
        String nome = CadastrarProdutoEmpresaUseCase.textoOuNulo(form.nome());
        if (nome != null && nome.length() < CadastrarProdutoEmpresaUseCase.NOME_MINIMO) {
            throw new CatalogException("nome_invalido", 400);
        }
        String tipo = CadastrarProdutoEmpresaUseCase.textoOuNulo(form.tipo()) == null
                ? null
                : EmpresaDoPortal.tipoValido(form.tipo());
        return new Alteracao(
                nome, tipo, CadastrarProdutoEmpresaUseCase.textoOuNulo(form.descricao()),
                CatalogException.codigoBarras(form.codBarras()));
    }

    /** `null` sem PDF; com PDF, confere que o produto pode ser alterado antes de guardar o PDF e chamar o extrator. */
    private FdsDoProduto.FdsPreparada fdsNova(Integer empresaId, Integer produtoId, FdsDoProduto.PdfDaFds pdf) {
        if (pdf == null) {
            return null;
        }
        exigirAlteravel(produtos.findDaEmpresa(produtoId, empresaId), produtoId);
        return fds.preparar(empresaId, pdf);
    }

    private ProdutoView gravar(
            Integer empresaId,
            Integer produtoId,
            Alteracao alteracao,
            byte[] imagem,
            FdsDoProduto.FdsPreparada fdsPreparada) {
        Produto produto = exigirAlteravel(produtos.findDaEmpresaParaAlterar(produtoId, empresaId), produtoId);
        if (alteracao.nome() != null) {
            produto.setNome(alteracao.nome());
        }
        if (alteracao.tipo() != null) {
            produto.setTipo(alteracao.tipo());
        }
        if (alteracao.descricao() != null) {
            produto.setDescricao(alteracao.descricao());
        }
        String codBarras = alteracao.codBarras();
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
            produto.setFotoUrl(imagens.enviar(imagem, ImagemDoProduto.PASTA_PRODUTOS, produto.getFotoUrl()));
        }
        if (fdsPreparada != null) {
            fds.registrar(produto.getId(), fdsPreparada);
        }
        return ProdutoMapper.view(produto);
    }

    /** Produto com vínculo ativo da empresa e sem vínculo ativo de outra. */
    private Produto exigirAlteravel(Optional<Produto> encontrado, Integer produtoId) {
        Produto produto = encontrado.orElseThrow(CatalogException::produtoNaoEncontrado);
        if (vinculos.contarAtivos(produtoId) > 1) {
            throw new CatalogException("produto_compartilhado", 409);
        }
        return produto;
    }
}
