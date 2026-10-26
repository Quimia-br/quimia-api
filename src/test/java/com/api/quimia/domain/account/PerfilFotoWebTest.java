package com.api.quimia.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.catalog.FakeImageStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** A foto é enviada pelo app direto ao Cloudinary; a API só apaga a antiga quando `PATCH /usuarios/me` a troca. */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(PerfilFotoWebTest.ArmazenamentoFalso.class)
class PerfilFotoWebTest {
    private static final String PASTA_PERFIL = FakeImageStorage.BASE + "quimia/perfil/";
    private static final String FOTO_UM = PASTA_PERFIL + "um.jpg";
    private static final String FOTO_DOIS = PASTA_PERFIL + "dois.jpg";

    @TestConfiguration
    static class ArmazenamentoFalso {
        @Bean
        @Primary
        FakeImageStorage fakeImageStorage() {
            return new FakeImageStorage();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ImageStorage storage;

    private FakeImageStorage armazenamento;
    private String token;

    @BeforeEach
    void seed() throws Exception {
        armazenamento = (FakeImageStorage) storage;
        armazenamento.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@perfilfoto.test'");
        token = TestLogins.usuario(mvc, "u@perfilfoto.test");
    }

    private void trocarFoto(String url) throws Exception {
        mvc.perform(patch("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fotoUrl\":\"" + url + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void primeiraFotoNaoApagaNada() throws Exception {
        trocarFoto(FOTO_UM);

        assertThat(armazenamento.apagadas()).isEmpty();
    }

    @Test
    void trocaApagaAFotoAnterior() throws Exception {
        trocarFoto(FOTO_UM);

        trocarFoto(FOTO_DOIS);

        assertThat(armazenamento.apagadas()).containsExactly(FOTO_UM);
    }

    @Test
    void enviarAMesmaFotoNaoApagaNada() throws Exception {
        trocarFoto(FOTO_UM);

        trocarFoto(FOTO_UM);

        assertThat(armazenamento.apagadas()).isEmpty();
    }

    @Test
    void fotoQueNaoEDaPastaDePerfilNaoEApagada() throws Exception {
        String doGoogle = "https://lh3.googleusercontent.com/a/foto";
        String deProduto = FakeImageStorage.PREFIXO + "42.png";

        for (String alheia : new String[] {doGoogle, deProduto}) {
            trocarFoto(alheia);
            trocarFoto(FOTO_UM);
            trocarFoto(FOTO_DOIS);
            trocarFoto(FOTO_UM);
        }

        assertThat(armazenamento.apagadas()).doesNotContain(doGoogle, deProduto);
    }

    @Test
    void alterarSoONomeNaoApagaAFoto() throws Exception {
        trocarFoto(FOTO_UM);

        mvc.perform(patch("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outro Nome\"}"))
                .andExpect(status().isOk());

        assertThat(armazenamento.apagadas()).isEmpty();
    }
}
