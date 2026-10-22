package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/** Com `quimia.fds.obrigatoria=true`: produto novo exige o PDF; vincular a um existente, não. */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@TestPropertySource(properties = "quimia.fds.obrigatoria=true")
@Import(FdsDoProdutoWebTest.ArmazenamentoFalso.class)
class FdsObrigatoriaWebTest {
    /** Substitui a definição do bean: a fábrica real recusaria subir com a flag ligada e sem URL do extrator. */
    @TestBean
    private ExtratorFds extratorFds;

    static ExtratorFds extratorFds() {
        return new FakeExtratorFds();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private String tokenOutra;

    @BeforeEach
    void seed() throws Exception {
        new TestCatalogo(jdbc).limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@fds-obrigatoria.test'");
        token = TestLogins.empresa(mvc, "Empresa Obrigatoria", "e@fds-obrigatoria.test", "30000015000110");
        tokenOutra = TestLogins.empresa(mvc, "Outra Obrigatoria", "o@fds-obrigatoria.test", "30000016000165");
    }

    @Test
    void produtoNovoSemFdsDa422ENaoCria() throws Exception {
        FdsDoProdutoWebTest.cadastrar(mvc, token, "7897770000017")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("fds_obrigatoria"));
        FdsDoProdutoWebTest.cadastrar(mvc, token, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("fds_obrigatoria"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM produto", Integer.class)).isZero();
    }

    @Test
    void comFdsCriaEVinculoAExistenteNaoExigeFds() throws Exception {
        FdsDoProdutoWebTest.cadastrar(mvc, tokenOutra, "7897770000024", FdsDoProdutoWebTest.pdf(FdsDoProdutoWebTest.PDF))
                .andExpect(status().isCreated());

        FdsDoProdutoWebTest.cadastrar(mvc, token, "7897770000024")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("vinculado"));
    }
}
