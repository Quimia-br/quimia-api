package com.api.quimia.domain.partner.internal.usecase;

import com.api.quimia.domain.account.LocalizacaoGateway;
import com.api.quimia.domain.account.dto.LocalizacaoView;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.partner.internal.dto.PontoView;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pontos de compra e descarte: `estado` e `bairro` filtram; o CEP ordena
 * (igual, depois mesmo prefixo de 5 dígitos, depois o resto). Ranking e paginação em memória.
 * As coordenadas só vão na resposta, para o mapa (nem todo ponto tem); não filtram nem ordenam.
 */
@Service
public class ListarPontosUseCase {
    public static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 50;
    static final Set<String> TIPOS = Set.of("compra", "descarte");
    private static final int RANK_CEP_IGUAL = 0;
    private static final int RANK_MESMO_PREFIXO = 1;
    private static final int RANK_DEMAIS = 2;
    /** Estado nulo vai para o fim da ordenação. */
    private static final String ESTADO_AUSENTE = "￿";

    private final PontosDoFiltro pontos;
    private final LocalizacaoGateway localizacao;

    public ListarPontosUseCase(PontosDoFiltro pontos, LocalizacaoGateway localizacao) {
        this.pontos = pontos;
        this.localizacao = localizacao;
    }

    @Transactional(readOnly = true)
    public Pagina<PontoView> execute(
            UUID usuarioId, String tipo, String estado, String bairro, String cep, boolean usarLocalizacaoCadastrada,
            int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO || (long) pagina * tamanho > Integer.MAX_VALUE) {
            throw new PartnerException("paginacao_invalida", 400);
        }
        if (tipo == null || !TIPOS.contains(tipo)) {
            throw new PartnerException("tipo_invalido", 400);
        }
        String uf = TextoPonto.estado(estado);
        String bairroBusca = TextoPonto.bairro(bairro);
        String cepBusca = TextoPonto.cep(cep);
        if (usarLocalizacaoCadastrada) {
            LocalizacaoView cadastrada = localizacao.doUsuario(usuarioId)
                    .orElseThrow(() -> new PartnerException("localizacao_necessaria", 422));
            uf = uf != null ? uf : estadoCadastrado(cadastrada.estado());
            bairroBusca = bairroBusca != null ? bairroBusca : TextoPonto.bairro(cadastrada.bairro());
            cepBusca = cepBusca != null ? cepBusca : TextoPonto.cepDoBanco(cadastrada.cep());
        }
        String cepFinal = cepBusca;
        List<PontoView> ordenados = pontos.buscar(tipo, uf, bairroBusca).stream()
                .sorted(Comparator.comparingInt((PontoView ponto) -> rank(ponto, cepFinal))
                        .thenComparing(ponto -> ponto.estado() == null ? ESTADO_AUSENTE : ponto.estado())
                        .thenComparing(ponto -> TextoPonto.semAcento(ponto.nome()))
                        .thenComparing(PontoView::id))
                .toList();
        int inicio = (int) Math.min((long) pagina * tamanho, ordenados.size());
        int fim = Math.min(inicio + tamanho, ordenados.size());
        List<PontoView> itens = ordenados.subList(inicio, fim);
        int totalPaginas = (int) Math.ceil(ordenados.size() / (double) tamanho);
        return new Pagina<>(itens, pagina, tamanho, ordenados.size(), totalPaginas);
    }

    /** UF cadastrada fora do formato é ignorada em vez de recusar a busca. */
    private static String estadoCadastrado(String estado) {
        try {
            return TextoPonto.estado(estado);
        } catch (PartnerException invalido) {
            return null;
        }
    }

    private static int rank(PontoView ponto, String cepBusca) {
        String cepDoPonto = TextoPonto.cepDoBanco(ponto.cep());
        if (cepBusca == null || cepDoPonto == null) {
            return RANK_DEMAIS;
        }
        if (cepDoPonto.equals(cepBusca)) {
            return RANK_CEP_IGUAL;
        }
        return TextoPonto.prefixo(cepDoPonto).equals(TextoPonto.prefixo(cepBusca)) ? RANK_MESMO_PREFIXO : RANK_DEMAIS;
    }
}
