package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.catalog.internal.dto.CadastroProdutoForm;
import com.api.quimia.domain.catalog.internal.dto.CadastroProdutoResponse;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.CodigoBarras;
import com.api.quimia.domain.catalog.internal.model.EmpresaProduto;
import com.api.quimia.domain.catalog.internal.model.Marca;
import com.api.quimia.domain.catalog.internal.model.Produto;
import com.api.quimia.domain.catalog.internal.persistence.EmpresaProdutoRepository;
import com.api.quimia.domain.catalog.internal.persistence.MarcaRepository;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cadastro pelo portal. A marca é sempre a da empresa (sem campo no formulário). Se o código de
 * barras já existe no catálogo, a empresa é vinculada ao produto existente em vez de duplicá-lo.
 *
 * A FDS (PDF) só é lida quando o produto vai ser criado: vincular a um produto existente mantém a FDS dele. O
 * extrator é chamado antes da transação (pode levar até um minuto); a gravação do produto, do vínculo e da FDS
 * acontece numa transação só.
 */
@Service
public class CadastrarProdutoEmpresaUseCase {
    public static final String CRIADO = "criado";
    public static final String VINCULADO = "vinculado";
    public static final String REATIVADO = "reativado";
    static final int NOME_MINIMO = 2;

    private final EmpresaDoPortal portal;
    private final ProdutoRepository produtos;
    private final MarcaRepository marcas;
    private final EmpresaProdutoRepository vinculos;
    private final ImagemDoProduto imagens;
    private final FdsDoProduto fds;
    private final TransactionTemplate transacao;

    public CadastrarProdutoEmpresaUseCase(
            EmpresaDoPortal portal,
            ProdutoRepository produtos,
            MarcaRepository marcas,
            EmpresaProdutoRepository vinculos,
            ImagemDoProduto imagens,
            FdsDoProduto fds,
            PlatformTransactionManager transacoes) {
        this.portal = portal;
        this.produtos = produtos;
        this.marcas = marcas;
        this.vinculos = vinculos;
        this.imagens = imagens;
        this.fds = fds;
        this.transacao = new TransactionTemplate(transacoes);
    }

    public CadastroProdutoResponse execute(Integer empresaId, CadastroProdutoForm form) {
        EmpresaView empresa = portal.exigirAtiva(empresaId);
        String nome = form.nome().trim();
        if (nome.length() < NOME_MINIMO) {
            throw new CatalogException("nome_invalido", 400);
        }
        String tipo = EmpresaDoPortal.tipoValido(form.tipo());
        String codBarras = CatalogException.codigoBarras(form.codBarras());
        byte[] imagem = imagens.ler(form.imagem());
        String fdsExtraida = codigoJaCadastrado(codBarras) ? null : fdsDoProdutoNovo(fds.ler(form.fds()));
        return transacao.execute(
                status -> gravar(empresaId, empresa, nome, tipo, codBarras, form.descricao(), imagem, fdsExtraida));
    }

    /** Leitura sem bloqueio, só para decidir se o extrator será chamado; a transação confere de novo. */
    private boolean codigoJaCadastrado(String codBarras) {
        return codBarras != null && !produtos.findByCodBarrasIn(CodigoBarras.equivalentes(codBarras)).isEmpty();
    }

    private String fdsDoProdutoNovo(byte[] pdf) {
        if (pdf == null) {
            if (fds.obrigatoria()) {
                throw new CatalogException("fds_obrigatoria", 422);
            }
            return null;
        }
        return fds.extrair(pdf);
    }

    /**
     * Se outro cadastro criou o mesmo código enquanto o extrator respondia, a empresa é vinculada a ele e a FDS
     * extraída é descartada, como no vínculo comum. Se o produto já existente sumiu nesse intervalo (código trocado),
     * a regra da FDS obrigatória vale de novo aqui.
     */
    private CadastroProdutoResponse gravar(
            Integer empresaId,
            EmpresaView empresa,
            String nome,
            String tipo,
            String codBarras,
            String descricao,
            byte[] imagem,
            String fdsExtraida) {
        if (codBarras != null) {
            Optional<Produto> existente = equivalente(produtos, codBarras);
            if (existente.isPresent()) {
                return vincular(empresaId, existente.get());
            }
        }
        if (fdsExtraida == null && fds.obrigatoria()) {
            throw new CatalogException("fds_obrigatoria", 422);
        }
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setDescricao(textoOuNulo(descricao));
        produto.setCodBarras(codBarras);
        produto.setMarca(marcaDa(empresa));
        if (imagem != null) {
            produto.setFotoUrl(imagens.enviar(imagem, ImagemDoProduto.PASTA_PRODUTOS, null));
        }
        produtos.save(produto);
        vinculos.save(novoVinculo(empresaId, produto.getId()));
        if (fdsExtraida != null) {
            fds.registrar(produto.getId(), fdsExtraida);
        }
        return new CadastroProdutoResponse(ProdutoMapper.view(produto), CRIADO);
    }

    private CadastroProdutoResponse vincular(Integer empresaId, Produto produto) {
        Optional<EmpresaProduto> vinculo = vinculos.findByEmpresaIdAndProdutoId(empresaId, produto.getId());
        if (vinculo.isPresent() && vinculo.get().isAtivo()) {
            throw new CatalogException("produto_ja_cadastrado", 409);
        }
        if (vinculo.isPresent()) {
            vinculo.get().setAtivo(true);
            return new CadastroProdutoResponse(ProdutoMapper.view(produto), REATIVADO);
        }
        vinculos.save(novoVinculo(empresaId, produto.getId()));
        return new CadastroProdutoResponse(ProdutoMapper.view(produto), VINCULADO);
    }

    /** Busca sem diferenciar maiúsculas; se não existir, cria de forma segura contra cadastros simultâneos. */
    private Marca marcaDa(EmpresaView empresa) {
        String nome = empresa.nome().trim();
        return marcas.findByNomeSemCaixa(nome).stream().findFirst().orElseGet(() -> {
            marcas.criarSeNaoExistir(nome);
            return marcas.findByNome(nome)
                    .orElseThrow(() -> new IllegalStateException("marca not found after insert: " + nome));
        });
    }

    private static EmpresaProduto novoVinculo(Integer empresaId, Integer produtoId) {
        EmpresaProduto vinculo = new EmpresaProduto();
        vinculo.setEmpresaId(empresaId);
        vinculo.setProdutoId(produtoId);
        vinculo.setAtivo(true);
        vinculo.setDataCadastro(LocalDateTime.now());
        return vinculo;
    }

    /**
     * Produto já cadastrado com o código ou com sua forma equivalente (UPC-A/EAN-13), preferindo o
     * exato. Bloqueia o código canônico até o fim da transação para que cadastros simultâneos das
     * duas formas não criem dois produtos.
     */
    static Optional<Produto> equivalente(ProdutoRepository produtos, String codBarras) {
        produtos.bloquearCodigo(CodigoBarras.canonico(codBarras).hashCode());
        List<Produto> encontrados = produtos.findByCodBarrasIn(CodigoBarras.equivalentes(codBarras));
        return encontrados.stream()
                .filter(produto -> codBarras.equals(produto.getCodBarras()))
                .findFirst()
                .or(() -> encontrados.stream().findFirst());
    }

    static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
