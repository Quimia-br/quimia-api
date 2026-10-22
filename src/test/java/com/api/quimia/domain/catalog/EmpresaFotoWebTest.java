package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.account.EmpresaPerfilGateway;
import com.api.quimia.domain.catalog.internal.image.ImageStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorageException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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

/** Usa o `EmpresaPerfilGateway` real de `account`; o espião só injeta falha nos testes que pedem. */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(EmpresaFotoWebTest.ArmazenamentoFalso.class)
class EmpresaFotoWebTest {
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'
    };
    private static final int LIMITE_DA_IMAGEM = 5 * 1024 * 1024;

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
    private EmpresaPerfilGateway perfil;

    private FakeImageStorage armazenamento;
    private String token;
    private int empresa;

    @BeforeEach
    void seed() throws Exception {
        armazenamento = (FakeImageStorage) storage;
        armazenamento.limpar();
        new TestCatalogo(jdbc).limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@foto.test'");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@foto.test'");
        token = TestLogins.empresa(mvc, "Empresa Foto", "e@foto.test", "30000009000163");
        empresa = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@foto.test'", Integer.class);
    }

    private ResultActions enviar(String tokenDeQuem, MockMultipartFile... arquivos) throws Exception {
        var pedido = multipart("/api/v1/empresas/me/foto").header("Authorization", "Bearer " + tokenDeQuem);
        for (MockMultipartFile arquivo : arquivos) {
            pedido.file(arquivo);
        }
        return mvc.perform(pedido);
    }

    private static MockMultipartFile imagem(byte[] conteudo) {
        return new MockMultipartFile("imagem", "logo.png", "image/png", conteudo);
    }

    private String fotoNoBanco() {
        return jdbc.queryForObject("SELECT foto_url FROM empresa WHERE id = ?", String.class, empresa);
    }

    private String pastaDaEmpresa() {
        return FakeImageStorage.BASE + "empresas/" + empresa + "/";
    }

    @Test
    void enviaParaAPastaDaEmpresaGravaEDevolveAUrl() throws Exception {
        enviar(token, imagem(PNG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(armazenamento.enviadas().get(0)));
        assertThat(armazenamento.enviadas().get(0)).startsWith(pastaDaEmpresa());
        assertThat(fotoNoBanco()).isEqualTo(armazenamento.enviadas().get(0));
        mvc.perform(get("/api/v1/empresas/me").header("Authorization", "Bearer " + token))
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

    /** `PATCH /empresas/me` aceita qualquer `fotoUrl` https: a troca não pode apagar imagem que não é da empresa. */
    @Test
    void naoApagaImagemDeProdutoNemLogoDeOutraEmpresaGravadosComoFotoAtual() throws Exception {
        String imagemDeProduto = FakeImageStorage.PREFIXO + "999.png";
        String logoDeOutra = FakeImageStorage.BASE + "empresas/" + (empresa + 1) + "/998.png";

        for (String alheia : new String[] {imagemDeProduto, logoDeOutra}) {
            jdbc.update("UPDATE empresa SET foto_url = ? WHERE id = ?", alheia, empresa);
            enviar(token, imagem(PNG)).andExpect(status().isOk());
        }

        assertThat(armazenamento.apagadas()).doesNotContain(imagemDeProduto, logoDeOutra);
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
    void falhaAoGravarDepoisDoUploadApagaANovaEMantemOLogoAntigo() throws Exception {
        enviar(token, imagem(PNG)).andExpect(status().isOk());
        String antigo = armazenamento.enviadas().get(0);
        doThrow(new IllegalStateException("falha ao gravar a foto")).when(perfil).atualizarFoto(anyInt(), anyString());

        enviar(token, imagem(PNG)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("erro_interno"));

        assertThat(armazenamento.apagadas()).containsExactly(armazenamento.enviadas().get(1));
        assertThat(fotoNoBanco()).isEqualTo(antigo);
    }

    @Test
    void urlMaiorQueAColunaDa502ApagaANovaEMantemOLogoAntigo() throws Exception {
        enviar(token, imagem(PNG)).andExpect(status().isOk());
        String antigo = armazenamento.enviadas().get(0);
        armazenamento.gerarUrlLonga();

        enviar(token, imagem(PNG)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("armazenamento_imagem_falhou"));

        assertThat(armazenamento.apagadas()).containsExactly(armazenamento.enviadas().get(1));
        assertThat(fotoNoBanco()).isEqualTo(antigo);
    }

    @Test
    void empresaApagadaDuranteOUploadDa404EApagaANova() throws Exception {
        doThrow(new IllegalArgumentException("empresa_nao_encontrada")).when(perfil).atualizarFoto(anyInt(), anyString());

        enviar(token, imagem(PNG)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("empresa_nao_encontrada"));

        assertThat(armazenamento.apagadas()).containsExactly(armazenamento.enviadas().get(0));
    }

    /** O upload leva segundos com a transação aberta: senha e `ativo` trocados nesse intervalo não são desfeitos. */
    @Test
    void trocaDeSenhaEDesativacaoDuranteOUploadSaoPreservadas() throws Exception {
        String senhaNova = "senha-trocada-durante-o-upload";
        armazenamento.duranteOUpload(() -> alterarEmOutraConexao(
                "UPDATE empresa SET senha = '" + senhaNova + "', ativo = FALSE WHERE id = " + empresa));

        enviar(token, imagem(PNG)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT senha FROM empresa WHERE id = ?", String.class, empresa)).isEqualTo(senhaNova);
        assertThat(jdbc.queryForObject("SELECT ativo FROM empresa WHERE id = ?", Boolean.class, empresa)).isFalse();
        assertThat(fotoNoBanco()).isEqualTo(armazenamento.enviadas().get(0));
    }

    @Test
    void autenticacaoEEmpresaInativa() throws Exception {
        mvc.perform(multipart("/api/v1/empresas/me/foto").file(imagem(PNG))).andExpect(status().isUnauthorized());
        String usuario = TestLogins.usuario(mvc, "u@foto.test");
        enviar(usuario, imagem(PNG)).andExpect(status().isForbidden());

        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE id = ?", empresa);
        enviar(token, imagem(PNG)).andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("empresa_inativa"));
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
