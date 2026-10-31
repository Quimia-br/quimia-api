package com.api.quimia.infra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.api.quimia.domain.catalog.dto.MarcaView;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import com.api.quimia.domain.catalog.internal.config.CacheDoCatalogo;
import com.api.quimia.domain.insights.internal.config.CacheDosInsights;
import com.api.quimia.domain.insights.internal.dto.ResumoEmpresaView;
import com.api.quimia.domain.insights.internal.dto.ResumoMisturas;
import com.api.quimia.domain.partner.internal.config.CacheDosPontos;
import com.api.quimia.domain.partner.internal.dto.PontoView;
import com.api.quimia.domain.safety.dto.ClasseQuimicaView;
import com.api.quimia.domain.safety.dto.ComposicaoView;
import com.api.quimia.domain.safety.dto.PrimeirosSocorrosView;
import com.api.quimia.domain.safety.dto.ResumoSimplificadoView;
import com.api.quimia.domain.safety.dto.SubstanciaView;
import com.api.quimia.domain.safety.internal.config.CacheDaSeguranca;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Montagem dos caches no Redis sem servidor: cada cache registrado com a validade do application.yml e o valor que
 * volta do JSON igual ao que foi gravado. O Redis só é contatado ao ler ou gravar, o que este teste não faz.
 */
class CachesNoRedisTest {
    private static final MarcaView MARCA = new MarcaView(5, "Limpa Bem", "https://cdn.test/marca.png");
    private static final ProdutoView PRODUTO = new ProdutoView(
            42, "Água Sanitária", "alvejante", "Cloro ativo 2,5%", "7891234567895", null, MARCA);

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, CacheAutoConfiguration.class))
            .withUserConfiguration(
                    CacheConfig.class, CacheDoCatalogo.class, CacheDaSeguranca.class, CacheDosPontos.class,
                    CacheDosInsights.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withPropertyValues(
                    "quimia.cache.enabled=true",
                    "spring.cache.type=redis",
                    "quimia.cache.prefix=quimia:teste:",
                    "quimia.cache.ttl.produto=1h",
                    "quimia.cache.ttl.produto-por-codigo=2h",
                    "quimia.cache.ttl.busca-de-produtos=10m",
                    "quimia.cache.ttl.substancia=24h",
                    "quimia.cache.ttl.composicao=3h",
                    "quimia.cache.ttl.resumo-da-fds=4h",
                    "quimia.cache.ttl.primeiros-socorros=5h",
                    "quimia.cache.ttl.pontos=6h",
                    "quimia.cache.ttl.resumo-da-empresa=5m");

    @Test
    void registraCadaCacheComAValidadeConfigurada() {
        contexto.run(ctx -> {
            Map<String, RedisCacheConfiguration> caches = ctx.getBean(RedisCacheManager.class).getCacheConfigurations();
            assertThat(caches).containsOnlyKeys(
                    "produto", "produto-por-codigo", "busca-de-produtos", "substancia", "composicao", "resumo-da-fds",
                    "primeiros-socorros", "pontos", "resumo-da-empresa");
            assertThat(validade(caches.get("produto"))).isEqualTo(Duration.ofHours(1));
            assertThat(validade(caches.get("produto-por-codigo"))).isEqualTo(Duration.ofHours(2));
            assertThat(validade(caches.get("busca-de-produtos"))).isEqualTo(Duration.ofMinutes(10));
            assertThat(validade(caches.get("substancia"))).isEqualTo(Duration.ofHours(24));
            assertThat(validade(caches.get("composicao"))).isEqualTo(Duration.ofHours(3));
            assertThat(validade(caches.get("resumo-da-fds"))).isEqualTo(Duration.ofHours(4));
            assertThat(validade(caches.get("primeiros-socorros"))).isEqualTo(Duration.ofHours(5));
            assertThat(validade(caches.get("pontos"))).isEqualTo(Duration.ofHours(6));
            assertThat(validade(caches.get("resumo-da-empresa"))).isEqualTo(Duration.ofMinutes(5));
            assertThat(caches.get("produto").getKeyPrefixFor("produto")).isEqualTo("quimia:teste:produto::");
            assertThat(caches.get("produto").getAllowCacheNullValues()).isFalse();
        });
    }

    @Test
    void cacheNaoRegistradoNaoECriado() {
        contexto.run(ctx -> assertThat(ctx.getBean(RedisCacheManager.class).getCache("nome-errado")).isNull());
    }

    @Test
    void desligadoNaoMontaCacheNenhum() {
        contexto.withPropertyValues("quimia.cache.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(RedisCacheManager.class));
    }

    @Test
    void cadaValorVoltaDoJsonIgualAoGravado() {
        LocalDate data = LocalDate.of(2026, 10, 1);
        Map<String, Object> exemplos = Map.of(
                "produto", PRODUTO,
                "produto-por-codigo", PRODUTO,
                "busca-de-produtos", new Pagina<>(List.of(PRODUTO), 0, 20, 1, 1),
                "substancia", new SubstanciaView(
                        7, "Hipoclorito de sódio", "7681-52-9", "Oxidante", List.of("Água de lavadeira"),
                        List.of(new ClasseQuimicaView(3, "Oxidantes", "Liberam oxigênio"))),
                "composicao", List.of(new ComposicaoView(
                        7, "Hipoclorito de sódio", "7681-52-9", new BigDecimal("2.0"), new BigDecimal("2.5"), "%",
                        List.of("Oxidantes"))),
                "resumo-da-fds", new ResumoSimplificadoView(42, "Não misture com ácidos.", "3.1", data),
                "primeiros-socorros", new PrimeirosSocorrosView(
                        42, true,
                        List.of(new PrimeirosSocorrosView.PrimeiroSocorroView(
                                "Olhos", "Lave com água", "Ardência", null, true)),
                        List.of("Procure o CIATox"), List.of(), "3.1", data),
                "pontos", List.of(new PontoView(
                        1, "Ecoponto Centro", "descarte", "01001-000", "SP", "Sé", "Praça da Sé", 100, null,
                        new BigDecimal("-23.5505"), new BigDecimal("-46.6333"))),
                "resumo-da-empresa", new ResumoEmpresaView(12, new ResumoMisturas(10, 7, 2, 1, 0.7778), PRODUTO));
        contexto.run(ctx -> {
            Map<String, RedisCacheConfiguration> caches = ctx.getBean(RedisCacheManager.class).getCacheConfigurations();
            exemplos.forEach((cache, valor) -> {
                ByteBuffer json = caches.get(cache).getValueSerializationPair().write(valor);
                assertThat(caches.get(cache).getValueSerializationPair().read(json)).as(cache).isEqualTo(valor);
            });
        });
    }

    private static Duration validade(RedisCacheConfiguration cache) {
        return cache.getTtlFunction().getTimeToLive("chave", "valor");
    }
}
