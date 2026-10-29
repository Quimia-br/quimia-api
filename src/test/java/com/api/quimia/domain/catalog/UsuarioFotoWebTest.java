package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.account.UsuarioPerfilGateway;
import com.api.quimia.domain.catalog.internal.image.ImageStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorageException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Usa o `UsuarioPerfilGateway` real de `account`; o espião só injeta falha nos testes que pedem. */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(UsuarioFotoWebTest.ArmazenamentoFalso.class)
class UsuarioFotoWebTest {
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'
    };
    private static final int LIMITE_DA_IMAGEM = 5 * 1024 * 1024;
    private static final String PASTA_PERFIL = FakeImageStorage.BASE + "quimia/perfil/";

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
    private DataSource dataSource;

    @Autowired
    private ImageStorage storage;

    @MockitoSpyBean
    private UsuarioPerfilGateway perfil;

    private FakeImageStorage armazenamento;
    private String token;
    private UUID usuario;

    @BeforeEach
    void seed() throws Exception {
        armazenamento = (FakeImageStorage) storage;
        armazenamento.limpar();
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@fotousuario.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@fotousuario.test'");
        token = TestLogins.usuario(mvc, "u@fotousuario.test");
        usuario = catalogo.usuarioId("u@fotousuario.test");
    }

    private ResultActions enviar(String tokenDeQuem, MockMultipartFile... arquivos) throws Exception {
        var pedido = multipart("/api/v1/usuarios/me/foto").header("Authorization", "Bearer " + tokenDeQuem);
        for (MockMultipartFile arquivo : arquivos) {
            pedido.file(arquivo);
        }
        return mvc.perform(pedido);
    }

    private static MockMultipartFile imagem(byte[] conteudo) {
        return new MockMultipartFile("imagem", "foto.png", "image/png", conteudo);
    }

    private String fotoNoBanco() {
        return jdbc.queryForObject("SELECT foto_url FROM usuario WHERE id = ?", String.class, usuario);
    }

    @Test
    void enviaParaAPastaDePerfilGravaEDevolveAUrl() throws Exception {
        enviar(token, imagem(PNG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(armazenamento.enviadas().get(0)));
        assertThat(armazenamento.enviadas().get(0)).startsWith(PASTA_PERFIL);
        assertThat(fotoNoBanco()).isEqualTo(armazenamento.enviadas().get(0));
        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.fotoUrl").value(armazenamento.enviadas().get(0)));
    }

    @Test
    void trocaApagaAAnteriorSoDepoisDoCommit() throws Exception {
        enviar(token, imagem(PNG)).andExpect(status().isOk());
        String primeira = armazenamento.enviadas().get(0);

        enviar(token, imagem(PNG)).andExpect(status().isOk());

        assertThat(fotoNoBanco()).isEqualTo(armazenamento.enviadas().get(1));
        assertThat(armazenamento.apagadas()).containsExactly(primeira);
    }

    /** `PATCH /usuarios/me` aceita qualquer `fotoUrl` https: outro perfil pode usar a mesma imagem. */
    @Test
    void naoApagaAFotoAnteriorQueOutroUsuarioAindaUsa() throws Exception {
        String compartilhada = PASTA_PERFIL + "999.png";
        TestLogins.usuario(mvc, "outro@fotousuario.test");
        jdbc.update("UPDATE usuario SET foto_url = ? WHERE email IN ('u@fotousuario.test', 'outro@fotousuario.test')",
                compartilhada);

        enviar(token, imagem(PNG)).andExpect(status().isOk());

        assertThat(armazenamento.apagadas()).doesNotContain(compartilhada);
        assertThat(fotoNoBanco()).isEqualTo(armazenamento.enviadas().get(0));
    }

    @Test
    void naoApagaFotoForaDaPastaDePerfil() throws Exception {
        String doGoogle = "https://lh3.googleusercontent.com/a/foto";
        String imagemDeProduto = FakeImageStorage.PREFIXO + "999.png";

        for (String alheia : new String[] {doGoogle, imagemDeProduto}) {
            jdbc.update("UPDATE usuario SET foto_url = ? WHERE id = ?", alheia, usuario);
            enviar(token, imagem(PNG)).andExpect(status().isOk());
        }

        assertThat(armazenamento.apagadas()).doesNotContain(doGoogle, imagemDeProduto);
    }

    @Test
    void semArquivoOuArquivoVazioDa400() throws Exception {
        enviar(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("imagem_obrigatoria"));
        enviar(token, imagem(new byte[0])).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("imagem_obrigatoria"));
    }

    @Test
    void arquivoQueNaoEImagemDa415EGrandeDemaisDa413() throws Exception {
        enviar(token, imagem("isto não é uma imagem de verdade".getBytes())).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("imagem_invalida"));
        byte[] grande = new byte[LIMITE_DA_IMAGEM + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);
        enviar(token, imagem(grande)).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("imagem_muito_grande"));
        assertThat(armazenamento.enviadas()).isEmpty();
    }

    @Test
    void armazenamentoForaDoArEIndisponivelDao502e503() throws Exception {
        armazenamento.falharComo(ImageStorageException.Motivo.FALHOU);
        enviar(token, imagem(PNG)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("armazenamento_imagem_falhou"));
        armazenamento.falharComo(ImageStorageException.Motivo.INDISPONIVEL);
        enviar(token, imagem(PNG)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("armazenamento_imagem_indisponivel"));
        assertThat(fotoNoBanco()).isNull();
    }

    @Test
    void falhaAoGravarDepoisDoUploadApagaANovaEMantemAFotoAntiga() throws Exception {
        enviar(token, imagem(PNG)).andExpect(status().isOk());
        String antiga = armazenamento.enviadas().get(0);
        doThrow(new IllegalStateException("falha ao gravar a foto")).when(perfil).atualizarFoto(any(), anyString());

        enviar(token, imagem(PNG)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("erro_interno"));

        assertThat(armazenamento.apagadas()).containsExactly(armazenamento.enviadas().get(1));
        assertThat(fotoNoBanco()).isEqualTo(antiga);
    }

    @Test
    void urlMaiorQueAColunaDa502ApagaANovaEMantemAFotoAntiga() throws Exception {
        enviar(token, imagem(PNG)).andExpect(status().isOk());
        String antiga = armazenamento.enviadas().get(0);
        armazenamento.gerarUrlLonga();

        enviar(token, imagem(PNG)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("armazenamento_imagem_falhou"));

        assertThat(armazenamento.apagadas()).containsExactly(armazenamento.enviadas().get(1));
        assertThat(fotoNoBanco()).isEqualTo(antiga);
    }

    @Test
    void usuarioApagadoDuranteOUploadDa404EApagaANova() throws Exception {
        doThrow(new IllegalArgumentException("usuario_nao_encontrado")).when(perfil).atualizarFoto(any(), anyString());

        enviar(token, imagem(PNG)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("usuario_nao_encontrado"));

        assertThat(armazenamento.apagadas()).containsExactly(armazenamento.enviadas().get(0));
    }

    /** O upload leva segundos com a transação aberta: senha e nome trocados nesse intervalo não são desfeitos. */
    @Test
    void trocaDeSenhaENomeDuranteOUploadSaoPreservadas() throws Exception {
        String senhaNova = "senha-trocada-durante-o-upload";
        armazenamento.duranteOUpload(() -> alterarEmOutraConexao(
                "UPDATE usuario SET senha = '" + senhaNova + "', nome = 'Nome novo' WHERE id = '" + usuario + "'"));

        enviar(token, imagem(PNG)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT senha FROM usuario WHERE id = ?", String.class, usuario))
                .isEqualTo(senhaNova);
        assertThat(jdbc.queryForObject("SELECT nome FROM usuario WHERE id = ?", String.class, usuario))
                .isEqualTo("Nome novo");
        assertThat(fotoNoBanco()).isEqualTo(armazenamento.enviadas().get(0));
    }

    @Test
    void semTokenEComTokenDeEmpresa() throws Exception {
        mvc.perform(multipart("/api/v1/usuarios/me/foto").file(imagem(PNG))).andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@fotousuario.test", "30000001000105");
        enviar(empresa, imagem(PNG)).andExpect(status().isForbidden());
        assertThat(armazenamento.enviadas()).isEmpty();
    }

    private void alterarEmOutraConexao(String sql) {
        try (Connection outra = dataSource.getConnection(); Statement comando = outra.createStatement()) {
            outra.setAutoCommit(true);
            comando.executeUpdate(sql);
        } catch (SQLException falha) {
            throw new IllegalStateException(falha);
        }
    }
}
