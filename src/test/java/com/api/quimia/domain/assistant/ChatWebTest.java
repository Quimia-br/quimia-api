package com.api.quimia.domain.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.TestLogins;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Kemi (app) e WebKemi (portal) contra um agente HTTP de verdade no loopback. */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class ChatWebTest {
    private static final int LIMITE_POR_HORA = 5;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AgenteDeTeste AGENTE = iniciarAgente();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestCatalogo catalogo;
    private String tokenA;
    private String tokenB;
    private UUID usuarioA;

    private static AgenteDeTeste iniciarAgente() {
        try {
            return new AgenteDeTeste();
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        }
    }

    @DynamicPropertySource
    static void agente(DynamicPropertyRegistry propriedades) {
        propriedades.add("quimia.agente.url", AGENTE::url);
        propriedades.add("quimia.agente.token", () -> "token-do-agente");
        propriedades.add("quimia.agente.timeout-segundos", () -> "1");
        propriedades.add("quimia.agente.mensagens-por-hora", () -> String.valueOf(LIMITE_POR_HORA));
    }

    @BeforeEach
    void seed() throws Exception {
        catalogo = new TestCatalogo(jdbc);
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@chat.test'");
        jdbc.update("DELETE FROM empresa WHERE email LIKE '%@chat.test'");
        tokenA = TestLogins.usuario(mvc, "a@chat.test");
        tokenB = TestLogins.usuario(mvc, "b@chat.test");
        usuarioA = catalogo.usuarioId("a@chat.test");
        AGENTE.responder(200, "{\"resposta\":\"Não misture cloro com amônia.\"}").atrasar(Duration.ZERO);
    }

    private ResultActions enviar(String rota, String token, String corpo) throws Exception {
        return mvc.perform(post(rota).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions doApp(String token, String corpo) throws Exception {
        return enviar("/api/v1/chat/mensagens", token, corpo);
    }

    private JsonNode ultimoPedidoAoAgente() {
        var pedidos = AGENTE.pedidos();
        return JSON.readTree(pedidos.get(pedidos.size() - 1).corpo());
    }

    @Test
    void appRecebeARespostaEOAgenteRecebeOUsuarioDoTokenEASessao() throws Exception {
        int antes = AGENTE.pedidos().size();

        doApp(tokenA, "{\"texto\":\"  Posso misturar cloro e amônia?  \",\"sessaoId\":\"conversa-1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Não misture cloro com amônia."))
                .andExpect(jsonPath("$.sessaoId").value("conversa-1"))
                .andExpect(jsonPath("$.respondidoEm").exists());

        assertThat(AGENTE.pedidos()).hasSize(antes + 1);
        assertThat(AGENTE.pedidos().get(antes).autorizacao()).isEqualTo("Bearer token-do-agente");
        JsonNode pedido = ultimoPedidoAoAgente();
        assertThat(pedido.path("user_id").asString()).isEqualTo(usuarioA.toString());
        assertThat(pedido.path("session_id").asString()).isEqualTo("conversa-1");
        assertThat(pedido.path("pergunta").asString()).isEqualTo("Posso misturar cloro e amônia?");
    }

    @Test
    void semSessaoAApiAbreUmaConversaNovaPorMensagem() throws Exception {
        String primeira = doApp(tokenA, "{\"texto\":\"oi\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String segunda = doApp(tokenA, "{\"texto\":\"oi\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String sessao1 = JSON.readTree(primeira).path("sessaoId").asString();
        String sessao2 = JSON.readTree(segunda).path("sessaoId").asString();
        assertThat(UUID.fromString(sessao1)).isNotEqualTo(UUID.fromString(sessao2));
        assertThat(ultimoPedidoAoAgente().path("session_id").asString()).isEqualTo(sessao2);
    }

    @Test
    void usuarioDoCorpoNuncaChegaAoAgente() throws Exception {
        doApp(tokenA, "{\"texto\":\"oi\",\"user_id\":\"" + UUID.randomUUID() + "\",\"usuarioId\":\"x\"}")
                .andExpect(status().isOk());

        assertThat(ultimoPedidoAoAgente().path("user_id").asString()).isEqualTo(usuarioA.toString());
    }

    @Test
    void portalUsaUmAutorFixoDerivadoDaEmpresa() throws Exception {
        String empresa = TestLogins.empresa(mvc, "e@chat.test", "30000018000154");
        Integer empresaId = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'e@chat.test'", Integer.class);

        enviar("/api/v1/empresas/me/chat/mensagens", empresa, "{\"texto\":\"Como cadastro um produto?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Não misture cloro com amônia."));

        UUID esperado = UUID.nameUUIDFromBytes(("empresa:" + empresaId).getBytes(StandardCharsets.UTF_8));
        assertThat(ultimoPedidoAoAgente().path("user_id").asString()).isEqualTo(esperado.toString());
    }

    @Test
    void empresaInativaNaoConversaENemChegaAoAgente() throws Exception {
        String empresa = TestLogins.empresa(mvc, "inativa@chat.test", "30000019000107");
        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE email = 'inativa@chat.test'");
        int antes = AGENTE.pedidos().size();

        enviar("/api/v1/empresas/me/chat/mensagens", empresa, "{\"texto\":\"oi\"}")
                .andExpect(status().isForbidden());

        assertThat(AGENTE.pedidos()).hasSize(antes);
    }

    @Test
    void empresaApagadaComTokenAindaValidoDa404() throws Exception {
        String empresa = TestLogins.empresa(mvc, "apagada@chat.test", "30000022000112");
        jdbc.update("DELETE FROM empresa WHERE email = 'apagada@chat.test'");

        enviar("/api/v1/empresas/me/chat/mensagens", empresa, "{\"texto\":\"oi\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("empresa_nao_encontrada"));
    }

    @Test
    void validacoesDoCorpo() throws Exception {
        int antes = AGENTE.pedidos().size();
        for (String corpo : new String[] {
            "{}", "{\"texto\":\"\"}", "{\"texto\":\"   \"}", "{\"texto\":null}", "{\"texto\":\"" + "a".repeat(2001) + "\"}",
            "{\"texto\":\"oi\",\"sessaoId\":\"\"}", "{\"texto\":\"oi\",\"sessaoId\":\"com espaço\"}",
            "{\"texto\":\"oi\",\"sessaoId\":\"../../x\"}", "{\"texto\":\"oi\",\"sessaoId\":\"" + "s".repeat(65) + "\"}"
        }) {
            doApp(tokenA, corpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("validation"));
        }
        doApp(tokenA, "{\"texto\":").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("corpo_invalido"));
        doApp(tokenA, "{\"texto\":\"" + "a".repeat(2000) + "\",\"sessaoId\":\"" + "s".repeat(64) + "\"}")
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/chat/mensagens").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.TEXT_PLAIN).content("oi"))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(AGENTE.pedidos()).hasSize(antes + 1);
    }

    @Test
    void autenticacaoEPerfilDeCadaRota() throws Exception {
        mvc.perform(post("/api/v1/chat/mensagens").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isUnauthorized());
        String empresa = TestLogins.empresa(mvc, "perfil@chat.test", "30000020000123");
        doApp(empresa, "{\"texto\":\"oi\"}").andExpect(status().isForbidden());
        enviar("/api/v1/empresas/me/chat/mensagens", tokenA, "{\"texto\":\"oi\"}").andExpect(status().isForbidden());
    }

    @Test
    void falhasDoAgenteViramCodigosEstaveis() throws Exception {
        AGENTE.responder(503, "{\"detail\":\"fora\"}");
        doApp(tokenA, "{\"texto\":\"oi\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("agente_indisponivel"));

        AGENTE.responder(500, "{\"detail\":\"erro\"}");
        doApp(tokenA, "{\"texto\":\"oi\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("agente_falhou"));

        AGENTE.responder(200, "{\"resposta\":\"\"}");
        doApp(tokenA, "{\"texto\":\"oi\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("agente_falhou"));

        AGENTE.responder(200, "{\"resposta\":\"tarde demais\"}").atrasar(Duration.ofMillis(2500));
        doApp(tokenB, "{\"texto\":\"oi\"}")
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.detail").value("agente_timeout"));
    }

    @Test
    void limitePorHoraValeParaCadaAutorSeparado() throws Exception {
        for (int i = 0; i < LIMITE_POR_HORA; i++) {
            doApp(tokenA, "{\"texto\":\"mensagem " + i + "\"}").andExpect(status().isOk());
        }
        int antes = AGENTE.pedidos().size();

        doApp(tokenA, "{\"texto\":\"uma a mais\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("limite_excedido"));
        assertThat(AGENTE.pedidos()).hasSize(antes);
        doApp(tokenB, "{\"texto\":\"outro usuário\"}").andExpect(status().isOk());
    }

    private ResultActions encerrar(String rota, String token) throws Exception {
        return mvc.perform(post(rota).header("Authorization", "Bearer " + token));
    }

    @Test
    void novoChatDoAppEncerraASessaoDoUsuarioDoTokenNoAgente() throws Exception {
        AGENTE.responder(200, "{\"session_id\":\"conversa-1\",\"status\":\"resumida\"}");
        int antes = AGENTE.pedidos().size();

        encerrar("/api/v1/chat/sessoes/conversa-1/encerrar", tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessaoId").value("conversa-1"))
                .andExpect(jsonPath("$.situacao").value("resumida"));

        assertThat(AGENTE.pedidos()).hasSize(antes + 1);
        AgenteDeTeste.Pedido pedido = AGENTE.pedidos().get(antes);
        assertThat(pedido.metodo()).isEqualTo("POST");
        assertThat(pedido.caminho()).isEqualTo("/chat/sessions/close");
        assertThat(pedido.autorizacao()).isEqualTo("Bearer token-do-agente");
        JsonNode corpo = JSON.readTree(pedido.corpo());
        assertThat(corpo.path("session_id").asString()).isEqualTo("conversa-1");
        assertThat(corpo.path("user_id").asString()).isEqualTo(usuarioA.toString());
        assertThat(corpo.size()).isEqualTo(2);
    }

    @Test
    void novoChatDoPortalUsaOAutorDerivadoDaEmpresa() throws Exception {
        AGENTE.responder(200, "{\"session_id\":\"s-1\",\"status\":\"ja_resumida\"}");
        String empresa = TestLogins.empresa(mvc, "enc@chat.test", "30000023000167");
        Integer empresaId = jdbc.queryForObject("SELECT id FROM empresa WHERE email = 'enc@chat.test'", Integer.class);

        encerrar("/api/v1/empresas/me/chat/sessoes/s-1/encerrar", empresa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("ja_resumida"));

        UUID esperado = UUID.nameUUIDFromBytes(("empresa:" + empresaId).getBytes(StandardCharsets.UTF_8));
        assertThat(ultimoPedidoAoAgente().path("user_id").asString()).isEqualTo(esperado.toString());
    }

    @Test
    void sessaoSemMensagensEPassadaAdiante() throws Exception {
        AGENTE.responder(200, "{\"session_id\":\"vazia\",\"status\":\"sem_turnos\"}");

        encerrar("/api/v1/chat/sessoes/vazia/encerrar", tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("sem_turnos"));
    }

    @Test
    void sessaoForaDoPadraoNemChegaAoAgente() throws Exception {
        int antes = AGENTE.pedidos().size();

        for (String sessao : new String[] {"a.b", "a@b", "s".repeat(65)}) {
            encerrar("/api/v1/chat/sessoes/" + sessao + "/encerrar", tokenA)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("sessao_invalida"));
        }

        assertThat(AGENTE.pedidos()).hasSize(antes);
    }

    @Test
    void encerramentoNaoConsomeOLimiteDeMensagens() throws Exception {
        AGENTE.responder(200, "{\"session_id\":\"x\",\"status\":\"resumida\"}");

        for (int i = 0; i <= LIMITE_POR_HORA; i++) {
            encerrar("/api/v1/chat/sessoes/x/encerrar", tokenB).andExpect(status().isOk());
        }
        AGENTE.responder(200, "{\"resposta\":\"ok\"}");
        doApp(tokenB, "{\"texto\":\"ainda posso perguntar\"}").andExpect(status().isOk());
    }

    @Test
    void falhasDoAgenteNoEncerramentoViramCodigosEstaveis() throws Exception {
        Object[][] casos = {
            {503, "{\"detail\":\"fora\"}", 503, "agente_indisponivel"},
            {500, "{\"detail\":\"erro\"}", 502, "agente_falhou"},
            {200, "{\"session_id\":\"x\",\"status\":\"qualquer\"}", 502, "agente_falhou"},
            {200, "{\"session_id\":\"x\"}", 502, "agente_falhou"},
            {200, "não é json", 502, "agente_falhou"},
        };
        for (Object[] caso : casos) {
            AGENTE.responder((Integer) caso[0], (String) caso[1]);
            encerrar("/api/v1/chat/sessoes/x/encerrar", tokenA)
                    .andExpect(status().is((Integer) caso[2]))
                    .andExpect(jsonPath("$.detail").value(caso[3]));
        }
    }

    @Test
    void encerramentoExigeLoginEOPerfilDeCadaRota() throws Exception {
        int antes = AGENTE.pedidos().size();
        String empresa = TestLogins.empresa(mvc, "perfil2@chat.test", "30000024000101");

        mvc.perform(post("/api/v1/chat/sessoes/x/encerrar")).andExpect(status().isUnauthorized());
        encerrar("/api/v1/chat/sessoes/x/encerrar", empresa).andExpect(status().isForbidden());
        encerrar("/api/v1/empresas/me/chat/sessoes/x/encerrar", tokenA).andExpect(status().isForbidden());
        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE email = 'perfil2@chat.test'");
        encerrar("/api/v1/empresas/me/chat/sessoes/x/encerrar", empresa)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("empresa_inativa"));

        assertThat(AGENTE.pedidos()).hasSize(antes);
    }
}
