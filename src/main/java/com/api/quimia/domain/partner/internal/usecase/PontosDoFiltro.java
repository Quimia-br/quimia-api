package com.api.quimia.domain.partner.internal.usecase;

import com.api.quimia.domain.partner.internal.config.CacheDosPontos;
import com.api.quimia.domain.partner.internal.dto.PontoView;
import com.api.quimia.domain.partner.internal.model.PontoParceiro;
import com.api.quimia.domain.partner.internal.persistence.PontoParceiroRepository;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pontos ativos de um tipo, estado e bairro, sem ordem. Fica no cache por filtro: a API não grava pontos (o DBA
 * altera direto no banco), então valem pela validade. A ordenação pelo CEP de quem busca é feita depois, em memória.
 */
@Component
public class PontosDoFiltro {
    private final PontoParceiroRepository pontos;

    public PontosDoFiltro(PontoParceiroRepository pontos) {
        this.pontos = pontos;
    }

    @Cacheable(
            cacheNames = CacheDosPontos.PONTOS,
            key = "T(com.api.quimia.domain.partner.internal.usecase.PontosDoFiltro).chave(#tipo, #estado, #bairro)")
    @Transactional(readOnly = true)
    public List<PontoView> buscar(String tipo, String estado, String bairro) {
        return pontos.buscar(tipo, estado, bairro).stream().map(PontosDoFiltro::view).toList();
    }

    /** Chave do cache sem ambiguidade: `null` não colide com o texto "null", nem um bairro com o separador. */
    public static String chave(String tipo, String estado, String bairro) {
        return parteDaChave(tipo) + parteDaChave(estado) + parteDaChave(bairro);
    }

    private static String parteDaChave(String valor) {
        return valor == null ? "-|" : valor.length() + ":" + valor + "|";
    }

    private static PontoView view(PontoParceiro ponto) {
        return new PontoView(
                ponto.getId(), ponto.getNome(), ponto.getTipo(), ponto.getCep(), ponto.getEstado(), ponto.getBairro(),
                ponto.getRua(), ponto.getNumero(), ponto.getComplemento(), ponto.getLatitude(), ponto.getLongitude());
    }
}
