package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.domain.catalog.dto.ProdutoView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = com.api.quimia.Application.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class CatalogEmpresaGatewayTest {
    @Autowired
    private CatalogEmpresaGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void soVinculosAtivosEOMaisRecentePorDataDeCadastro() {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@gw-catalogo.test'");
        int empresa = catalogo.empresa("Empresa", "a@gw-catalogo.test");
        int outra = catalogo.empresa("Outra", "b@gw-catalogo.test");
        int marca = catalogo.marca("Marca", null);
        int antigo = catalogo.produto("Antigo", marca, "limpeza_geral", null, null);
        int recente = catalogo.produto("Recente", marca, "desinfetante", null, null);
        int nulo = catalogo.produto("Ativo nulo", marca, "outro", null, null);
        int desativado = catalogo.produto("Desativado", marca, "outro", null, null);
        int daOutra = catalogo.produto("Da outra", marca, "outro", null, null);
        catalogo.vinculo(empresa, antigo, "TRUE");
        catalogo.vinculo(empresa, recente, "TRUE");
        catalogo.vinculo(empresa, nulo, "NULL");
        catalogo.vinculo(empresa, desativado, "FALSE");
        catalogo.vinculo(outra, daOutra, "TRUE");
        jdbc.update("UPDATE empresa_produto SET data_cadastro = TIMESTAMP '2026-01-01 10:00:00' WHERE id_produto = ?", antigo);
        jdbc.update("UPDATE empresa_produto SET data_cadastro = TIMESTAMP '2026-05-01 10:00:00' WHERE id_produto = ?", recente);
        jdbc.update("UPDATE empresa_produto SET data_cadastro = TIMESTAMP '2026-09-01 10:00:00' WHERE id_produto = ?", desativado);
        jdbc.update("UPDATE empresa_produto SET data_cadastro = TIMESTAMP '2026-02-01 10:00:00' WHERE id_produto = ?", nulo);

        assertThat(gateway.produtoIdsAtivos(empresa)).containsExactlyInAnyOrder(antigo, recente, nulo);
        ProdutoView destaque = gateway.produtoMaisRecente(empresa).orElseThrow();
        assertThat(destaque.id()).isEqualTo(recente);
        assertThat(destaque.marca().nome()).isEqualTo("Marca");
        assertThat(gateway.produtoIdsAtivos(outra)).containsExactly(daOutra);
    }

    @Test
    void mesmaDataDesempataPeloMaiorIdEDataNulaFicaPorUltimo() {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@gw-catalogo.test'");
        int empresa = catalogo.empresa("Empate", "d@gw-catalogo.test");
        int marca = catalogo.marca("Marca", null);
        int semData = catalogo.produto("Sem data", marca, "outro", null, null);
        int primeiro = catalogo.produto("Primeiro", marca, "outro", null, null);
        int segundo = catalogo.produto("Segundo", marca, "outro", null, null);
        for (int produto : new int[] {semData, primeiro, segundo}) {
            catalogo.vinculo(empresa, produto, "TRUE");
        }
        jdbc.update("UPDATE empresa_produto SET data_cadastro = NULL WHERE id_produto = ?", semData);
        jdbc.update("UPDATE empresa_produto SET data_cadastro = TIMESTAMP '2026-03-01 10:00:00' WHERE id_produto IN (?, ?)",
                primeiro, segundo);

        assertThat(gateway.produtoMaisRecente(empresa).orElseThrow().id()).isEqualTo(Math.max(primeiro, segundo));

        jdbc.update("UPDATE empresa_produto SET ativo = FALSE WHERE id_produto IN (?, ?)", primeiro, segundo);
        assertThat(gateway.produtoMaisRecente(empresa).orElseThrow().id()).isEqualTo(semData);
    }

    @Test
    void empresaSemProdutosDevolveVazio() {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@gw-catalogo.test'");
        int sozinha = catalogo.empresa("Sozinha", "c@gw-catalogo.test");

        assertThat(gateway.produtoIdsAtivos(sozinha)).isEmpty();
        assertThat(gateway.produtoMaisRecente(sozinha)).isEmpty();
        assertThat(gateway.produtoIdsAtivos(999999)).isEmpty();
    }
}
