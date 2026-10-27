package com.api.quimia.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.account.dto.LocalizacaoView;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {com.api.quimia.Application.class, AccountTestConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class LocalizacaoGatewayTest {
    @Autowired
    private LocalizacaoGateway gateway;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private UUID usuario;
    private UUID outro;

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("DELETE FROM localizacao_usuario WHERE id_usuario IN (SELECT id FROM usuario WHERE email LIKE '%@gw-local.test')");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@gw-local.test'");
        token = TestLogins.usuario(mvc, "a@gw-local.test");
        TestLogins.usuario(mvc, "b@gw-local.test");
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        usuario = catalogo.usuarioId("a@gw-local.test");
        outro = catalogo.usuarioId("b@gw-local.test");
    }

    @Test
    void devolveOEnderecoCadastrado() throws Exception {
        mvc.perform(put("/api/v1/usuarios/me/localizacao").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cep\":\"01001-000\",\"estado\":\"SP\",\"bairro\":\"Sé\",\"rua\":\"Praça da Sé\",\"numero\":10,\"complemento\":\"Lado A\"}"))
                .andExpect(status().isOk());

        LocalizacaoView view = gateway.doUsuario(usuario).orElseThrow();

        assertThat(view).isEqualTo(new LocalizacaoView("01001-000", "SP", "Sé", "Praça da Sé", 10, "Lado A", null, null));
    }

    @Test
    void devolveAsCoordenadasParaOsOutrosModulos() throws Exception {
        mvc.perform(put("/api/v1/usuarios/me/localizacao").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"SP\",\"latitude\":-23.55052,\"longitude\":-46.633309}"))
                .andExpect(status().isOk());

        LocalizacaoView view = gateway.doUsuario(usuario).orElseThrow();

        assertThat(view.latitude()).isEqualByComparingTo("-23.55052");
        assertThat(view.longitude()).isEqualByComparingTo("-46.633309");
    }

    @Test
    void camposNaoInformadosVemNulos() throws Exception {
        mvc.perform(put("/api/v1/usuarios/me/localizacao").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"RJ\"}"))
                .andExpect(status().isOk());

        LocalizacaoView view = gateway.doUsuario(usuario).orElseThrow();

        assertThat(view.estado()).isEqualTo("RJ");
        assertThat(view.cep()).isNull();
        assertThat(view.bairro()).isNull();
        assertThat(view.numero()).isNull();
    }

    @Test
    void semCadastroOuUsuarioInexistenteDevolveVazio() {
        assertThat(gateway.doUsuario(usuario)).isEmpty();
        assertThat(gateway.doUsuario(UUID.randomUUID())).isEmpty();
    }

    @Test
    void naoVazaOEnderecoDeOutroUsuario() throws Exception {
        mvc.perform(put("/api/v1/usuarios/me/localizacao").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"SP\"}"))
                .andExpect(status().isOk());

        assertThat(gateway.doUsuario(outro)).isEmpty();
    }
}
