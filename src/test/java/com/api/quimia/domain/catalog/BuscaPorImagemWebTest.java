package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import com.api.quimia.domain.catalog.internal.rotulo.CandidatoDoRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotuloException.Motivo;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** `POST /produtos/busca-por-imagem` com o agente trocado por um dublê programável. */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(BuscaPorImagemWebTest.AgenteFalso.class)
class BuscaPorImagemWebTest {
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'
    };
    private static final String ROTA = "/api/v1/produtos/busca-por-imagem";

    @TestConfiguration
    static class AgenteFalso {
        @Bean
        @Primary
        FakeLeitorDeRotulo fakeLeitorDeRotulo() {
            return new FakeLeitorDeRotulo();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LeitorDeRotulo leitorInjetado;

    private FakeLeitorDeRotulo agente;
    private TestCatalogo catalogo;
    private String token;
    private int marcaX;
    private int marcaY;
    private int aguaSanitaria;

    @BeforeEach
    void seed() throws Exception {
        agente = (FakeLeitorDeRotulo) leitorInjetado;
        agente.limpar();
        catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@rotulo.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@rotulo.test'");
        token = TestLogins.usuario(mvc, "u@rotulo.test");
        marcaX = catalogo.marca("Marca X", null);
        marcaY = catalogo.marca("Marca Y", null);
        aguaSanitaria = catalogo.produto("Água Sanitária", marcaX, "limpeza_geral", "7891000100103", null);
    }

    private ResultActions escanear(String tokenDeQuem, MockMultipartFile... arquivos) throws Exception {
        var pedido = multipart(ROTA).header("Authorization", "Bearer " + tokenDeQuem);
        for (MockMultipartFile arquivo : arquivos) {
            pedido.file(arquivo);
        }
        return mvc.perform(pedido);
    }

    private ResultActions escanear() throws Exception {
        return escanear(token, new MockMultipartFile("imagem", "rotulo.png", "image/png", PNG));
    }

    @Test
    void achaOProdutoPeloCodigoDeBarrasLidoNoRotulo() throws Exception {
        agente.ler(new CandidatoDoRotulo("qualquer nome", null, "7891000100103"));

        escanear().andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos.length()").value(1))
                .andExpect(jsonPath("$.produtos[0].id").value(aguaSanitaria))
                .andExpect(jsonPath("$.produtos[0].nome").value("Água Sanitária"))
                .andExpect(jsonPath("$.produtos[0].marca.nome").value("Marca X"))
                .andExpect(jsonPath("$.naoIdentificados").value(0));
        assertThat(agente.chamadas()).isEqualTo(1);
    }

    @Test
    void upcAEEan13ComZeroSaoOMesmoCodigo() throws Exception {
        int upc = catalogo.produto("Detergente", marcaY, "limpeza_geral", "036000291452", null);
        agente.ler(new CandidatoDoRotulo("Detergente", null, "0036000291452"));

        escanear().andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos[0].id").value(upc))
                .andExpect(jsonPath("$.naoIdentificados").value(0));
    }

    @Test
    void semCodigoValidoAchaPeloNomeEMarcaSemAcentoNemCaixa() throws Exception {
        agente.ler(new CandidatoDoRotulo("agua SANITARIA", "marca x", "ABC123"));

        escanear().andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos[0].id").value(aguaSanitaria))
                .andExpect(jsonPath("$.naoIdentificados").value(0));
    }

    @Test
    void nomeSemMarcaSoCasaQuandoOProdutoEUnico() throws Exception {
        agente.ler(new CandidatoDoRotulo("Água Sanitária", null, null));
        escanear().andExpect(jsonPath("$.produtos[0].id").value(aguaSanitaria));

        int outraMarca = catalogo.produto("Água Sanitária", marcaY, "limpeza_geral", null, null);
        escanear().andExpect(jsonPath("$.produtos.length()").value(0)).andExpect(jsonPath("$.naoIdentificados").value(1));

        agente.ler(new CandidatoDoRotulo("Água Sanitária", "Marca Y", null));
        escanear().andExpect(jsonPath("$.produtos[0].id").value(outraMarca));
    }

    @Test
    void nomeParcialOuMarcaDiferenteNaoCasa() throws Exception {
        agente.ler(
                new CandidatoDoRotulo("Água", null, null),
                new CandidatoDoRotulo("Água Sanitária 2L", null, null),
                new CandidatoDoRotulo("Água Sanitária", "Marca Y", null));

        escanear().andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos.length()").value(0))
                .andExpect(jsonPath("$.naoIdentificados").value(3));
    }

    @Test
    void mesmoProdutoLidoDuasVezesApareceUmaVezEOQueNaoCasouEContado() throws Exception {
        agente.ler(
                new CandidatoDoRotulo("Água Sanitária", "Marca X", null),
                new CandidatoDoRotulo("Outro nome", null, "7891000100103"),
                new CandidatoDoRotulo("Produto que não existe", null, null));

        escanear().andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos.length()").value(1))
                .andExpect(jsonPath("$.naoIdentificados").value(1));
    }

    @Test
    void agenteSemNadaLidoDevolveListaVazia() throws Exception {
        escanear().andExpect(status().isOk())
                .andExpect(jsonPath("$.produtos.length()").value(0))
                .andExpect(jsonPath("$.naoIdentificados").value(0));
    }

    @Test
    void falhasDoAgenteViram503504e502() throws Exception {
        agente.falharComo(Motivo.INDISPONIVEL);
        escanear().andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value("agente_indisponivel"));
        agente.falharComo(Motivo.TEMPO_ESGOTADO);
        escanear().andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.detail").value("agente_timeout"));
        agente.falharComo(Motivo.FALHOU);
        escanear().andExpect(status().isBadGateway()).andExpect(jsonPath("$.detail").value("agente_falhou"));
    }

    @Test
    void semArquivoOuArquivoQueNaoEImagemNaoChegamAoAgente() throws Exception {
        escanear(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("imagem_obrigatoria"));
        escanear(token, new MockMultipartFile("imagem", "rotulo.png", "image/png", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("imagem_obrigatoria"));
        escanear(token, new MockMultipartFile("imagem", "rotulo.png", "image/png", "isto não é imagem".getBytes()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("imagem_invalida"));
        assertThat(agente.chamadas()).isZero();
    }

    @Test
    void exigeUsuarioAutenticado() throws Exception {
        mvc.perform(multipart(ROTA).file(new MockMultipartFile("imagem", "rotulo.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "e@rotulo.test", "30000050000130");
        escanear(empresa, new MockMultipartFile("imagem", "rotulo.png", "image/png", PNG))
                .andExpect(status().isForbidden());
        assertThat(agente.chamadas()).isZero();
    }
}
