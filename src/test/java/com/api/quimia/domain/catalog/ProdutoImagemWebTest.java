package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.catalog.internal.image.ImageStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorageException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Imagem do produto no cadastro e na alteração do portal, com o Cloudinary trocado por um
 * registro em memória. Cobre validação, compensação em rollback e troca da imagem antiga.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(ProdutoImagemWebTest.ArmazenamentoFalso.class)
class ProdutoImagemWebTest {
    private static final String BASE = "/api/v1/empresas/me/produtos";
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'
    };
    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1
    };
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
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
    private ImageStorage storage;

    private FakeImageStorage armazenamento;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void seed() throws Exception {
        armazenamento = (FakeImageStorage) storage;
        armazenamento.limpar();
        new TestCatalogo(jdbc).limpar();
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@imagem.test'");
        tokenA = TestLogins.empresa(mvc, "Omo Brasil", "a@imagem.test", "55.667.788/0001-86");
        tokenB = TestLogins.empresa(mvc, "Veja Ltda", "b@imagem.test", "66.778.899/0001-86");
    }

    @Test
    void cadastraComImagemGuardandoAUrlDoArmazenamento() throws Exception {
        cadastrar(tokenA, "Limpa Vidros", "7895000000001", imagem(PNG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produto.fotoUrl").value(armazenamento.enviadas().get(0)));

        assertThat(armazenamento.enviadas()).hasSize(1);
        assertThat(armazenamento.apagadas()).isEmpty();
        assertThat(jdbc.queryForObject(
                        "SELECT foto_url FROM produto WHERE cod_barras = '7895000000001'", String.class))
                .startsWith(FakeImageStorage.PREFIXO);
    }

    @Test
    void aceitaJpegEWebp() throws Exception {
        cadastrar(tokenA, "Com Jpeg", "7895000000002", imagem(JPEG)).andExpect(status().isCreated());
        cadastrar(tokenA, "Com Webp", "7895000000003", imagem(WEBP)).andExpect(status().isCreated());

        assertThat(armazenamento.enviadas()).hasSize(2);
    }

    @Test
    void cadastraSemImagemSemTocarNoArmazenamento() throws Exception {
        cadastrar(tokenA, "Sem Foto", "7895000000004", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produto.fotoUrl").doesNotExist());

        assertThat(armazenamento.enviadas()).isEmpty();
    }

    @Test
    void rejeitaArquivoQueNaoEImagemPeloConteudoMesmoComTipoDeImagem() throws Exception {
        MockMultipartFile falso = new MockMultipartFile("imagem", "foto.png", "image/png", "isto é texto".getBytes());

        cadastrar(tokenA, "Falso", "7895000000005", falso)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("imagem_invalida"));

        assertThat(armazenamento.enviadas()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM produto", Integer.class)).isZero();
    }

    @Test
    void rejeitaImagemAcimaDoLimite() throws Exception {
        byte[] grande = new byte[LIMITE_DA_IMAGEM + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);

        cadastrar(tokenA, "Grande", "7895000000006", new MockMultipartFile("imagem", "g.png", "image/png", grande))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.detail").value("imagem_muito_grande"));

        assertThat(armazenamento.enviadas()).isEmpty();
    }

    @Test
    void falhaDepoisDoUploadApagaAImagemEnviada() throws Exception {
        armazenamento.gerarUrlLonga();

        cadastrar(tokenA, "Vai Falhar", "7895000000007", imagem(PNG)).andExpect(status().isConflict());

        assertThat(armazenamento.enviadas()).hasSize(1);
        assertThat(armazenamento.apagadas()).containsExactlyElementsOf(armazenamento.enviadas());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM produto", Integer.class)).isZero();
    }

    @Test
    void armazenamentoForaDoArNaoCriaOProduto() throws Exception {
        armazenamento.falharComo(ImageStorageException.Motivo.FALHOU);
        cadastrar(tokenA, "Sem Rede", "7895000000013", imagem(PNG))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("armazenamento_imagem_falhou"));

        armazenamento.falharComo(ImageStorageException.Motivo.INDISPONIVEL);
        cadastrar(tokenA, "Sem Chaves", "7895000000014", imagem(PNG))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("armazenamento_imagem_indisponivel"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM produto", Integer.class)).isZero();
    }

    @Test
    void vincularAProdutoExistenteIgnoraAImagem() throws Exception {
        cadastrar(tokenA, "Original", "7895000000008", null).andExpect(status().isCreated());

        cadastrar(tokenB, "Outro Nome", "7895000000008", imagem(PNG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("vinculado"))
                .andExpect(jsonPath("$.produto.fotoUrl").doesNotExist());

        assertThat(armazenamento.enviadas()).isEmpty();
    }

    @Test
    void alterarComImagemTrocaAUrlEApagaAAntigaDepoisDoCommit() throws Exception {
        int id = idDe(cadastrar(tokenA, "Trocar Foto", "7895000000009", imagem(PNG)));
        String antiga = armazenamento.enviadas().get(0);

        alterar(tokenA, id, imagem(JPEG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(armazenamento.enviadas().get(1)));

        assertThat(armazenamento.enviadas()).hasSize(2);
        assertThat(armazenamento.apagadas()).containsExactly(antiga);
        assertThat(jdbc.queryForObject("SELECT foto_url FROM produto WHERE id = ?", String.class, id))
                .isEqualTo(armazenamento.enviadas().get(1));
    }

    @Test
    void alterarSemImagemMantemAAtual() throws Exception {
        int id = idDe(cadastrar(tokenA, "Mantem Foto", "7895000000010", imagem(PNG)));

        alterar(tokenA, id, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(armazenamento.enviadas().get(0)));

        assertThat(armazenamento.apagadas()).isEmpty();
    }

    @Test
    void produtoCompartilhadoNaoRecebeImagemNovaNemFazUpload() throws Exception {
        int id = idDe(cadastrar(tokenA, "Compartilhado", "7895000000011", null));
        cadastrar(tokenB, "Compartilhado", "7895000000011", null).andExpect(status().isOk());

        alterar(tokenA, id, imagem(PNG))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("produto_compartilhado"));

        assertThat(armazenamento.enviadas()).isEmpty();
    }

    @Test
    void falhaAoAlterarDesfazOUploadENaoApagaAImagemAtual() throws Exception {
        int id = idDe(cadastrar(tokenA, "Falha Troca", "7895000000012", imagem(PNG)));
        String atual = armazenamento.enviadas().get(0);
        armazenamento.gerarUrlLonga();

        alterar(tokenA, id, imagem(JPEG)).andExpect(status().isConflict());

        assertThat(armazenamento.apagadas()).containsExactly(armazenamento.enviadas().get(1));
        assertThat(jdbc.queryForObject("SELECT foto_url FROM produto WHERE id = ?", String.class, id))
                .isEqualTo(atual);
    }

    private static MockMultipartFile imagem(byte[] conteudo) {
        return new MockMultipartFile("imagem", "foto", "application/octet-stream", conteudo);
    }

    private ResultActions cadastrar(String token, String nome, String codBarras, MockMultipartFile imagem)
            throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(BASE)
                .param("nome", nome)
                .param("tipo", "outro")
                .param("codBarras", codBarras);
        if (imagem != null) {
            request.file(imagem);
        }
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions alterar(String token, int id, MockMultipartFile imagem) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PATCH, BASE + "/" + id);
        if (imagem != null) {
            request.file(imagem);
        }
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private static int idDe(ResultActions cadastro) throws Exception {
        return JsonPath.read(cadastro.andReturn().getResponse().getContentAsString(), "$.produto.id");
    }
}
