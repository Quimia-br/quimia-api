package com.api.quimia.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Cache das leituras no Redis, ligado por `quimia.cache.enabled`. Cada módulo registra os próprios caches (validade e
 * tipo do valor) em `domain/<módulo>/internal/config/`; aqui ficam as regras comuns. Cache não registrado é erro:
 * nome digitado errado não vira um cache sem validade.
 *
 * O cache envolve a transação: um acerto não abre conexão com o Postgres, e o `@CacheEvict` de um caso de uso
 * `@Transactional` roda depois do commit. Um leitor que leu antes do commit ainda pode regravar o dado antigo; a
 * validade do cache limita esse atraso.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("quimia.cache.enabled")
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Chaves apagadas por lote no `allEntries`: SCAN em vez de KEYS, que bloqueia o Redis enquanto varre. */
    private static final int CHAVES_POR_LOTE = 1000;

    @Bean
    RedisCacheConfiguration padraoDosCaches(@Value("${quimia.cache.prefix}") String prefixo) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(prefixo)
                .disableCachingNullValues();
    }

    @Bean
    RedisCacheManagerBuilderCustomizer regrasComunsDosCaches(RedisConnectionFactory conexoes) {
        return builder -> builder
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(conexoes, BatchStrategies.scan(CHAVES_POR_LOTE)))
                .disableCreateOnMissingCache();
    }

    /** Redis fora do ar não derruba a API: a leitura vai ao Postgres e a falha fica no log. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new FalhaDoCacheNoLog();
    }

    static final class FalhaDoCacheNoLog implements CacheErrorHandler {
        @Override
        public void handleCacheGetError(RuntimeException erro, Cache cache, Object chave) {
            log.warn("cache {} falhou na leitura de {}; lido do banco: {}", cache.getName(), chave, erro.toString());
        }

        @Override
        public void handleCachePutError(RuntimeException erro, Cache cache, Object chave, Object valor) {
            log.warn("cache {} falhou ao gravar {}: {}", cache.getName(), chave, erro.toString());
        }

        /** Remoção que falhou deixa o dado antigo até vencer a validade do cache. */
        @Override
        public void handleCacheEvictError(RuntimeException erro, Cache cache, Object chave) {
            log.error("cache {} falhou ao remover {}; dado antigo até vencer o TTL: {}",
                    cache.getName(), chave, erro.toString());
        }

        @Override
        public void handleCacheClearError(RuntimeException erro, Cache cache) {
            log.error("cache {} falhou ao limpar; dados antigos até vencer o TTL: {}", cache.getName(), erro.toString());
        }
    }
}
