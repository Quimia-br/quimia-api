package com.api.quimia.domain.partner.internal.config;

import com.api.quimia.domain.partner.internal.dto.PontoView;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/** Cache dos pontos parceiros por filtro. A API não grava pontos: valem pela validade. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("quimia.cache.enabled")
public class CacheDosPontos {
    public static final String PONTOS = "pontos";

    @Bean
    RedisCacheManagerBuilderCustomizer pontosNoRedis(
            JsonMapper json, @Value("${quimia.cache.ttl.pontos}") Duration validade) {
        JavaType lista = json.getTypeFactory().constructCollectionType(List.class, PontoView.class);
        return builder -> builder.withCacheConfiguration(PONTOS, builder.cacheDefaults()
                .entryTtl(validade)
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(json, lista))));
    }
}
