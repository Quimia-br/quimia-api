package com.api.quimia.domain.safety.internal.persistence;

import com.api.quimia.domain.catalog.event.FdsRecebidaEvent;
import com.api.quimia.domain.safety.internal.config.CacheDaSeguranca;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FDS nova muda a composição, o resumo e os primeiros socorros do produto: os caches dele saem depois do commit, para
 * que um leitor não regrave a FDS anterior enquanto a nova ainda não está visível. Sem cache ligado, não faz nada.
 */
@Component
public class CacheDaFdsRecebida {
    @TransactionalEventListener
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheDaSeguranca.COMPOSICAO, key = "#evento.produtoId()"),
        @CacheEvict(cacheNames = CacheDaSeguranca.RESUMO_DA_FDS, key = "#evento.produtoId()"),
        @CacheEvict(cacheNames = CacheDaSeguranca.PRIMEIROS_SOCORROS, key = "#evento.produtoId()")
    })
    public void limpar(FdsRecebidaEvent evento) {
        // A limpeza é feita pelos @CacheEvict.
    }
}
