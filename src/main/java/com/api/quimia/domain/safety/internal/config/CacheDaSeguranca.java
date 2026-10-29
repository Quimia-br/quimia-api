package com.api.quimia.domain.safety.internal.config;

import com.api.quimia.domain.safety.dto.ComposicaoView;
import com.api.quimia.domain.safety.dto.PrimeirosSocorrosView;
import com.api.quimia.domain.safety.dto.ResumoSimplificadoView;
import com.api.quimia.domain.safety.dto.SubstanciaView;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Caches da segurança, por produto. A FDS nova do portal limpa os do produto; a composição é gravada pelo gatilho do
 * banco na mesma transação. Substâncias só mudam pelo DBA: valem pela validade.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("quimia.cache.enabled")
public class CacheDaSeguranca {
    public static final String SUBSTANCIA = "substancia";
    public static final String COMPOSICAO = "composicao";
    public static final String RESUMO_DA_FDS = "resumo-da-fds";
    public static final String PRIMEIROS_SOCORROS = "primeiros-socorros";

    @Bean
    RedisCacheManagerBuilderCustomizer cachesDaSeguranca(
            JsonMapper json,
            @Value("${quimia.cache.ttl.substancia}") Duration substancia,
            @Value("${quimia.cache.ttl.composicao}") Duration composicao,
            @Value("${quimia.cache.ttl.resumo-da-fds}") Duration resumo,
            @Value("${quimia.cache.ttl.primeiros-socorros}") Duration primeirosSocorros) {
        JavaType umaSubstancia = json.constructType(SubstanciaView.class);
        JavaType listaDaComposicao = json.getTypeFactory().constructCollectionType(List.class, ComposicaoView.class);
        JavaType umResumo = json.constructType(ResumoSimplificadoView.class);
        JavaType osPrimeirosSocorros = json.constructType(PrimeirosSocorrosView.class);
        return builder -> {
            RedisCacheConfiguration padrao = builder.cacheDefaults();
            builder.withCacheConfiguration(SUBSTANCIA, com(padrao, substancia, json, umaSubstancia))
                    .withCacheConfiguration(COMPOSICAO, com(padrao, composicao, json, listaDaComposicao))
                    .withCacheConfiguration(RESUMO_DA_FDS, com(padrao, resumo, json, umResumo))
                    .withCacheConfiguration(PRIMEIROS_SOCORROS, com(padrao, primeirosSocorros, json, osPrimeirosSocorros));
        };
    }

    /** O valor vai como JSON do próprio tipo, sem nome de classe: o JSON não carrega quem instanciar. */
    private static RedisCacheConfiguration com(
            RedisCacheConfiguration padrao, Duration validade, JsonMapper json, JavaType tipo) {
        return padrao.entryTtl(validade)
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(json, tipo)));
    }
}
