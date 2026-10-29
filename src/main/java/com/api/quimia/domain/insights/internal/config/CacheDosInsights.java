package com.api.quimia.domain.insights.internal.config;

import com.api.quimia.domain.insights.internal.dto.ResumoEmpresaView;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

/** Cache do painel da empresa (`/resumo` e `/estatisticas`). Vale pela validade: o painel tolera minutos de atraso. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("quimia.cache.enabled")
public class CacheDosInsights {
    public static final String RESUMO_DA_EMPRESA = "resumo-da-empresa";

    @Bean
    RedisCacheManagerBuilderCustomizer resumoDaEmpresaNoRedis(
            JsonMapper json, @Value("${quimia.cache.ttl.resumo-da-empresa}") Duration validade) {
        JacksonJsonRedisSerializer<ResumoEmpresaView> valor =
                new JacksonJsonRedisSerializer<>(json, ResumoEmpresaView.class);
        return builder -> builder.withCacheConfiguration(RESUMO_DA_EMPRESA, builder.cacheDefaults()
                .entryTtl(validade)
                .serializeValuesWith(SerializationPair.fromSerializer(valor)));
    }
}
