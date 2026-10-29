package com.api.quimia.domain.catalog.internal.config;

import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import java.time.Duration;
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
 * Caches do catálogo do app. Qualquer cadastro, alteração ou remoção no portal limpa os três: um vínculo novo ou
 * desativado muda quem aparece em todas as buscas.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("quimia.cache.enabled")
public class CacheDoCatalogo {
    public static final String PRODUTO = "produto";
    public static final String PRODUTO_POR_CODIGO = "produto-por-codigo";
    public static final String BUSCA_DE_PRODUTOS = "busca-de-produtos";

    @Bean
    RedisCacheManagerBuilderCustomizer cachesDoCatalogo(
            JsonMapper json,
            @Value("${quimia.cache.ttl.produto}") Duration produto,
            @Value("${quimia.cache.ttl.produto-por-codigo}") Duration produtoPorCodigo,
            @Value("${quimia.cache.ttl.busca-de-produtos}") Duration busca) {
        JavaType umProduto = json.constructType(ProdutoView.class);
        JavaType pagina = json.getTypeFactory().constructParametricType(Pagina.class, ProdutoView.class);
        return builder -> {
            RedisCacheConfiguration padrao = builder.cacheDefaults();
            builder.withCacheConfiguration(PRODUTO, com(padrao, produto, json, umProduto))
                    .withCacheConfiguration(PRODUTO_POR_CODIGO, com(padrao, produtoPorCodigo, json, umProduto))
                    .withCacheConfiguration(BUSCA_DE_PRODUTOS, com(padrao, busca, json, pagina));
        };
    }

    /** O valor vai como JSON do próprio tipo, sem nome de classe: o JSON não carrega quem instanciar. */
    private static RedisCacheConfiguration com(
            RedisCacheConfiguration padrao, Duration validade, JsonMapper json, JavaType tipo) {
        return padrao.entryTtl(validade)
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(json, tipo)));
    }
}
