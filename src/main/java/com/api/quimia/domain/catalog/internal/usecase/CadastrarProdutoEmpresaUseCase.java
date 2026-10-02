package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.catalog.internal.dto.CadastroProdutoForm;
import com.api.quimia.domain.catalog.internal.dto.CadastroProdutoResponse;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.EmpresaProduto;
import com.api.quimia.domain.catalog.internal.model.Marca;
import com.api.quimia.domain.catalog.internal.model.Produto;
import com.api.quimia.domain.catalog.internal.persistence.EmpresaProdutoRepository;
import com.api.quimia.domain.catalog.internal.persistence.MarcaRepository;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro pelo portal. A marca é sempre a da empresa (sem campo no formulário). Se o código de
 * barras já existe no catálogo, a empresa é vinculada ao produto existente em vez de duplicá-lo.
 */
@Service
public class CadastrarProdutoEmpresaUseCase {
    public static final String CRIADO = "criado";
    public static final String VINCULADO = "vinculado";
    public static final String REATIVADO = "reativado";

    private final EmpresaDoPortal portal;
    private final ProdutoRepository produtos;
    private final MarcaRepository marcas;
    private final EmpresaProdutoRepository vinculos;

    public CadastrarProdutoEmpresaUseCase(
            EmpresaDoPortal portal,
            ProdutoRepository produtos,
            MarcaRepository marcas,
            EmpresaProdutoRepository vinculos) {
        this.portal = portal;
        this.produtos = produtos;
        this.marcas = marcas;
        this.vinculos = vinculos;
    }

    @Transactional
    public CadastroProdutoResponse execute(Integer empresaId, CadastroProdutoForm form) {
        EmpresaView empresa = portal.exigirAtiva(empresaId);
        String tipo = EmpresaDoPortal.tipoValido(form.tipo());
        String codBarras = CatalogException.codigoBarras(form.codBarras());
        if (codBarras != null) {
            Optional<Produto> existente = produtos.findByCodBarras(codBarras);
            if (existente.isPresent()) {
                return vincular(empresaId, existente.get());
            }
        }
        Produto produto = new Produto();
        produto.setNome(form.nome().trim());
        produto.setTipo(tipo);
        produto.setDescricao(textoOuNulo(form.descricao()));
        produto.setCodBarras(codBarras);
        produto.setMarca(marcaDa(empresa));
        produtos.save(produto);
        vinculos.save(novoVinculo(empresaId, produto.getId()));
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

    static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
