package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.dto.BuscaPorImagemView;
import com.api.quimia.domain.catalog.internal.mapper.ProdutoMapper;
import com.api.quimia.domain.catalog.internal.model.CodigoBarras;
import com.api.quimia.domain.catalog.internal.model.Produto;
import com.api.quimia.domain.catalog.internal.model.TermoBusca;
import com.api.quimia.domain.catalog.internal.persistence.ProdutoRepository;
import com.api.quimia.domain.catalog.internal.rotulo.CandidatoDoRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotuloException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Escanear o rótulo pela foto: o agente lê os produtos na imagem e cada um é procurado no catálogo, primeiro pelo
 * código de barras e depois pelo nome (e a marca, quando lida). Só casa o que é igual, sem acento nem caixa: um
 * produto errado na estante é pior que um não identificado.
 *
 * Sem `@Transactional`: a chamada ao agente leva segundos e não deve segurar uma conexão do banco.
 */
@Service
public class BuscarProdutosPorImagemUseCase {
    private static final int CANDIDATOS_POR_NOME = 20;

    private final ImagemDoProduto imagens;
    private final LeitorDeRotulo leitor;
    private final ProdutoRepository produtos;
    private final LimiteDeBuscasPorImagem limite;

    public BuscarProdutosPorImagemUseCase(
            ImagemDoProduto imagens, LeitorDeRotulo leitor, ProdutoRepository produtos, LimiteDeBuscasPorImagem limite) {
        this.imagens = imagens;
        this.leitor = leitor;
        this.produtos = produtos;
        this.limite = limite;
    }

    /** Só a busca que chega ao agente conta no limite: imagem ausente ou inválida é recusada antes. */
    public BuscaPorImagemView execute(UUID usuarioId, MultipartFile arquivo) {
        byte[] imagem = imagens.ler(arquivo);
        if (imagem == null) {
            throw new CatalogException("imagem_obrigatoria", 400);
        }
        if (!limite.aceitar(usuarioId)) {
            throw new CatalogException("limite_excedido", 429);
        }
        List<CandidatoDoRotulo> lidos = identificar(imagem);
        Map<Integer, ProdutoView> achados = new LinkedHashMap<>();
        int naoIdentificados = 0;
        for (CandidatoDoRotulo candidato : lidos) {
            Optional<Produto> produto = porCodigo(candidato.codBarras()).or(() -> porNome(candidato));
            if (produto.isPresent()) {
                achados.putIfAbsent(produto.get().getId(), ProdutoMapper.view(produto.get()));
            } else {
                naoIdentificados++;
            }
        }
        return new BuscaPorImagemView(List.copyOf(achados.values()), naoIdentificados);
    }

    private List<CandidatoDoRotulo> identificar(byte[] imagem) {
        try {
            return leitor.identificar(imagem);
        } catch (LeitorDeRotuloException falha) {
            throw switch (falha.motivo()) {
                case INDISPONIVEL -> new CatalogException("agente_indisponivel", 503);
                case TEMPO_ESGOTADO -> new CatalogException("agente_timeout", 504);
                case FALHOU -> new CatalogException("agente_falhou", 502);
            };
        }
    }

    /** Como a busca por código de barras: UPC-A e EAN-13 com `0` são o mesmo código. Código ilegível não casa. */
    private Optional<Produto> porCodigo(String codBarras) {
        String codigo;
        try {
            codigo = CodigoBarras.normalizar(codBarras);
        } catch (IllegalArgumentException ilegivel) {
            return Optional.empty();
        }
        if (codigo == null) {
            return Optional.empty();
        }
        List<Produto> encontrados = produtos.findVisiveisPorCodigos(CodigoBarras.equivalentes(codigo));
        return encontrados.stream()
                .filter(produto -> codigo.equals(produto.getCodBarras()))
                .findFirst()
                .or(() -> encontrados.stream().findFirst());
    }

    /**
     * Nome igual ao lido. Com marca lida, a marca também tem de ser igual; sem marca, o nome tem de ser de um único
     * produto: dois com o mesmo nome em marcas diferentes não permitem escolher.
     */
    private Optional<Produto> porNome(CandidatoDoRotulo candidato) {
        String nome = TermoBusca.normalizado(candidato.nome());
        if (nome == null) {
            return Optional.empty();
        }
        String marca = TermoBusca.normalizado(candidato.marca());
        CamposDaBusca campos = CamposDaBusca.DO_APP;
        List<Produto> iguais = produtos
                .buscarVisiveis(
                        TermoBusca.like(candidato.nome()),
                        campos.nome(),
                        false,
                        false,
                        CamposDaBusca.tiposDoTermo(null),
                        false,
                        null,
                        true,
                        CodigoBarras.paraFiltro(null),
                        PageRequest.of(0, CANDIDATOS_POR_NOME, Sort.by("nome").and(Sort.by("id"))))
                .getContent()
                .stream()
                .filter(produto -> nome.equals(TermoBusca.normalizado(produto.getNome())))
                .toList();
        if (marca != null) {
            return iguais.stream()
                    .filter(produto -> marca.equals(TermoBusca.normalizado(produto.getMarca().getNome())))
                    .findFirst();
        }
        return iguais.size() == 1 ? Optional.of(iguais.get(0)) : Optional.empty();
    }
}
