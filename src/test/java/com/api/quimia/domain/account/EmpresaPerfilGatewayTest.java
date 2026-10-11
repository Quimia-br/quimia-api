package com.api.quimia.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = {com.api.quimia.Application.class, AccountTestConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class EmpresaPerfilGatewayTest {
    private static final String URL = "https://res.cloudinary.com/demo/image/upload/v1/produtos/logo.png";

    @Autowired
    private EmpresaPerfilGateway gateway;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transacoes;

    private String token;
    private int empresa;

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@gw-perfil.test'");
        token = TestLogins.empresa(mvc, "Empresa Perfil", "e@gw-perfil.test", "30000010000198");
        empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@gw-perfil.test'", Integer.class);
    }

    @Test
    void semFotoDevolveVazio() {
        assertThat(gateway.fotoAtual(empresa)).isEmpty();
    }

    @Test
    void empresaInexistenteDevolveVazio() {
        assertThat(gateway.fotoAtual(999999)).isEmpty();
    }

    @Test
    void atualizarGravaEAFotoApareceNoPerfil() throws Exception {
        gateway.atualizarFoto(empresa, URL);

        assertThat(gateway.fotoAtual(empresa)).contains(URL);
        mvc.perform(get("/api/v1/empresas/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.fotoUrl").value(URL));
    }

    @Test
    void atualizarSobrescreveAAnterior() {
        gateway.atualizarFoto(empresa, URL);
        gateway.atualizarFoto(empresa, URL + "?v=2");

        assertThat(gateway.fotoAtual(empresa)).contains(URL + "?v=2");
    }

    /**
     * O catalog lê a foto, envia a imagem (segundos) e só então grava a URL, tudo na mesma transação. O que outra
     * transação mudou na empresa nesse intervalo (senha, ativo) não pode ser desfeito pela gravação da foto.
     */
    @Test
    void gravarAFotoNaoDesfazAlteracaoFeitaPorOutraTransacaoDuranteOUpload() {
        String senhaNova = "senha-trocada-durante-o-upload";

        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            gateway.fotoAtual(empresa);
            alterarEmOutraConexao("UPDATE empresa SET senha = '" + senhaNova + "', ativo = FALSE WHERE id = " + empresa);
            gateway.atualizarFoto(empresa, URL);
        });

        assertThat(jdbc.queryForObject("SELECT senha FROM empresa WHERE id = ?", String.class, empresa)).isEqualTo(senhaNova);
        assertThat(jdbc.queryForObject("SELECT ativo FROM empresa WHERE id = ?", Boolean.class, empresa)).isFalse();
        assertThat(gateway.fotoAtual(empresa)).contains(URL);
    }

    private void alterarEmOutraConexao(String sql) {
        try (Connection outra = dataSource.getConnection(); Statement comando = outra.createStatement()) {
            outra.setAutoCommit(true);
            comando.executeUpdate(sql);
        } catch (SQLException falha) {
            throw new IllegalStateException(falha);
        }
    }

    @Test
    void atualizarEmpresaInexistenteLancaIllegalArgument() {
        assertThatThrownBy(() -> gateway.atualizarFoto(999999, URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("empresa_nao_encontrada");
    }
}
