package com.api.quimia.infra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.catalog.event.FdsRecebidaEvent;
import com.api.quimia.domain.catalog.internal.usecase.ListarProdutosUseCase;
import com.api.quimia.domain.partner.internal.usecase.PontosDoFiltro;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O cache ligado, pela pilha HTTP: o dado alterado direto no banco continua o do cache até a API limpar. O cache fica
 * em memória com as mesmas anotações; a montagem no Redis é coberta por {@link CachesNoRedisTest}.
 */
@SpringBootTest(classes = com.api.quimia.Application.class, properties = "quimia.cache.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CacheDasLeiturasWebTest.CacheEmMemoria.class)
class CacheDasLeiturasWebTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class CacheEmMemoria {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheManager caches;

    @Autowired
    private ApplicationEventPublisher eventos;

    @Autowired
    private TransactionTemplate transacao;

    private TestCatalogo catalogo;
    private String usuarioToken;
    private String empresaToken;
    private int empresa;
    private int produto;

    @BeforeEach
    void seed() throws Exception {
        caches.getCacheNames().forEach(nome -> caches.getCache(nome).clear());
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@cache.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@cache.test'");
        usuarioToken = TestLogins.usuario(mvc, "app@cache.test");
        empresaToken = TestLogins.empresa(mvc, "Marca do Cache", "portal@cache.test", "40000007000128");
        empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'portal@cache.test'", Integer.class);
        int marca = catalogo.marca("Marca do Cache", null);
        produto = catalogo.produto("Desengordurante", marca, "desengraxante", "7891000000101", "Remove gordura");
        catalogo.vinculo(empresa, produto, "TRUE");
    }

    @Test
    void produtoFicaNoCacheAteOPortalRemover() throws Exception {
        detalhe().andExpect(jsonPath("$.nome").value("Desengordurante"));
        busca().andExpect(jsonPath("$.totalItens").value(1));

        jdbc.update("UPDATE produto SET nome = 'Renomeado no banco' WHERE id = ?", produto);
        detalhe().andExpect(jsonPath("$.nome").value("Desengordurante"));

        mvc.perform(delete("/api/v1/empresas/me/produtos/{id}", produto).header("Authorization", "Bearer " + empresaToken))
                .andExpect(status().isNoContent());
        detalhe().andExpect(status().isNotFound());
        busca().andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void composicaoSaiDoCacheQuandoChegaFdsNova() throws Exception {
        int hipoclorito = catalogo.substancia("Hipoclorito de sódio", "7681-52-9", null);
        int fds = catalogo.fds(produto, "v1", LocalDate.of(2026, 1, 1), true, "Não misture com ácidos.");
        catalogo.composto(fds, hipoclorito, "2.00", "2.50", "%");
        composicao().andExpect(jsonPath("$.length()").value(1));

        jdbc.update("DELETE FROM fds_composto WHERE id_fds = ?", fds);
        composicao().andExpect(jsonPath("$.length()").value(1));

        transacao.executeWithoutResult(status ->
                eventos.publishEvent(new FdsRecebidaEvent(produto, "{}", "https://cdn.test/fds-nova.pdf")));
        composicao().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void empresaDesativadaPerdeOPainelMesmoComOResumoNoCache() throws Exception {
        resumo().andExpect(status().isOk());

        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE id = ?", empresa);
        resumo().andExpect(status().isForbidden());
    }

    @Test
    void chavesNaoConfundemNuloComTextoNemTermoComSeparador() {
        assertThat(ListarProdutosUseCase.chaveDaBusca(null, null, null, 0, 20))
                .isNotEqualTo(ListarProdutosUseCase.chaveDaBusca("null", null, null, 0, 20));
        assertThat(ListarProdutosUseCase.chaveDaBusca("a|b", null, null, 0, 20))
                .isNotEqualTo(ListarProdutosUseCase.chaveDaBusca("a", "b", null, 0, 20));
        assertThat(PontosDoFiltro.chave("descarte", null, "centro"))
                .isNotEqualTo(PontosDoFiltro.chave("descarte", "centro", null));
    }

    private ResultActions detalhe() throws Exception {
        return mvc.perform(get("/api/v1/produtos/{id}", produto).header("Authorization", "Bearer " + usuarioToken));
    }

    private ResultActions busca() throws Exception {
        return mvc.perform(get("/api/v1/produtos").param("q", "desengord")
                .header("Authorization", "Bearer " + usuarioToken));
    }

    private ResultActions composicao() throws Exception {
        return mvc.perform(get("/api/v1/produtos/{id}/substancias", produto)
                .header("Authorization", "Bearer " + usuarioToken));
    }

    private ResultActions resumo() throws Exception {
        return mvc.perform(get("/api/v1/empresas/me/resumo").header("Authorization", "Bearer " + empresaToken));
    }
}
