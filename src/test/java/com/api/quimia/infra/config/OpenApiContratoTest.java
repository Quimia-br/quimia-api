package com.api.quimia.infra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.TestJwtKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O documento OpenAPI acompanha as rotas de verdade: nenhuma rota fica de fora, toda rota diz o que faz e cada erro
 * documentado existe (e cada erro lançado pelos módulos está documentado). A segurança documentada é conferida com
 * requisições sem token. O documento gerado fica em `target/openapi.json`.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class OpenApiContratoTest {
    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");
    private static final Pattern CODIGO = Pattern.compile("`([a-z][a-z0-9_]*)`");
    private static final Pattern CODIGO_LANCADO =
            Pattern.compile("\\b(?:(?:Account|Catalog|Safety|Storage|Recommendation|Partner|Insights|Assistant)Exception|"
                    + "escrever)\\([^\"]*\"([a-z][a-z0-9_]*)\"");
    private static final Path FONTES = Path.of("src", "main", "java");
    /** Códigos que não são de nenhuma rota: o firewall recusa a URL antes de achar a rota. */
    private static final Set<String> CODIGOS_FORA_DAS_ROTAS = Set.of(
            "requisicao_rejeitada", "rota_nao_encontrada", "metodo_nao_permitido", "formato_nao_aceito",
            "requisicao_invalida", "valor_invalido", "conflito", "not_found");

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping rotas;

    private JsonNode documento;

    @BeforeEach
    void carregarDocumento() throws Exception {
        String corpo = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(Path.of("target", "openapi.json"), corpo, StandardCharsets.UTF_8);
        documento = JsonMapper.builder().build().readTree(corpo);
    }

    private record Operacao(String metodo, String caminho, JsonNode corpo) {
        String nome() {
            return metodo.toUpperCase() + " " + caminho;
        }
    }

    private List<Operacao> operacoes() {
        List<Operacao> operacoes = new ArrayList<>();
        documento.path("paths").properties().forEach(caminho -> caminho.getValue().properties().forEach(metodo -> {
            if (METODOS.contains(metodo.getKey())) {
                operacoes.add(new Operacao(metodo.getKey(), caminho.getKey(), metodo.getValue()));
            }
        }));
        return operacoes;
    }

    private static Set<String> codigos(String descricao) {
        Set<String> codigos = new TreeSet<>();
        Matcher encontrado = CODIGO.matcher(descricao);
        while (encontrado.find()) {
            codigos.add(encontrado.group(1));
        }
        return codigos;
    }

    /** Rotas da aplicação, com o caminho no formato do OpenAPI (`{id}`). */
    private Set<String> rotasDaAplicacao() {
        Set<String> nomes = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> rota : rotas.getHandlerMethods().entrySet()) {
            if (!rota.getValue().getBeanType().getName().startsWith("com.api.quimia.domain.")) {
                continue;
            }
            for (String caminho : rota.getKey().getPatternValues()) {
                rota.getKey().getMethodsCondition().getMethods()
                        .forEach(metodo -> nomes.add(metodo.name() + " " + caminho));
            }
        }
        return nomes;
    }

    @Test
    void documentoEInterfaceSaoPublicos() throws Exception {
        assertThat(documento.path("info").path("title").asString()).isEqualTo("Quimia API");
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void todaRotaDaAplicacaoEstaDocumentadaENadaAlemDelas() {
        Set<String> documentadas = operacoes().stream().map(Operacao::nome).collect(Collectors.toCollection(TreeSet::new));

        assertThat(documentadas).containsExactlyElementsOf(rotasDaAplicacao());
        assertThat(documentadas).hasSize(67);
    }

    @Test
    void todaRotaTemResumoDescricaoSecaoEIdUnico() {
        Set<String> secoes = OpenApiConfig.SECOES.stream().map(tag -> tag.getName()).collect(Collectors.toSet());
        Set<String> ids = new HashSet<>();
        for (Operacao operacao : operacoes()) {
            JsonNode corpo = operacao.corpo();
            assertThat(corpo.path("summary").asString("")).as(operacao.nome()).isNotBlank();
            assertThat(corpo.path("description").asString("")).as(operacao.nome()).isNotBlank();
            assertThat(corpo.path("tags").size()).as(operacao.nome()).isEqualTo(1);
            assertThat(secoes).as(operacao.nome()).contains(corpo.path("tags").get(0).asString());
            assertThat(ids.add(corpo.path("operationId").asString())).as("operationId repetido em " + operacao.nome())
                    .isTrue();
        }
    }

    @Test
    void todaRotaTemSucessoEErrosNoFormatoDoContrato() {
        for (Operacao operacao : operacoes()) {
            JsonNode respostas = operacao.corpo().path("responses");
            assertThat(respostas.propertyNames().stream().anyMatch(status -> status.startsWith("2")))
                    .as("sem resposta de sucesso: " + operacao.nome())
                    .isTrue();
            assertThat(respostas.has("500")).as(operacao.nome()).isTrue();
            respostas.properties().forEach(resposta -> {
                String contexto = operacao.nome() + " " + resposta.getKey();
                assertThat(resposta.getValue().path("description").asString("")).as(contexto).isNotBlank();
                if (Integer.parseInt(resposta.getKey()) < 400) {
                    return;
                }
                JsonNode corpo = resposta.getValue().path("content").path("application/problem+json");
                assertThat(corpo.path("schema").path("$ref").asString()).as(contexto).isEqualTo(OpenApiConfig.PROBLEMA_REF);
                Set<String> codigos = codigos(resposta.getValue().path("description").asString());
                assertThat(codigos).as("erro sem código: " + contexto).isNotEmpty();
                assertThat(corpo.path("examples").propertyNames()).as(contexto).containsExactlyInAnyOrderElementsOf(codigos);
                corpo.path("examples").properties().forEach(exemplo -> assertThat(
                                exemplo.getValue().path("value").path("detail").asString())
                        .isEqualTo(exemplo.getKey()));
            });
        }
    }

    @Test
    void segurancaDocumentadaBateComARespostaSemToken() throws Exception {
        for (Operacao operacao : operacoes()) {
            JsonNode seguranca = operacao.corpo().path("security");
            boolean exigeToken = seguranca.isMissingNode()
                    || seguranca.valueStream().anyMatch(requisito -> requisito.has(OpenApiConfig.BEARER));
            String caminho = operacao.caminho().replaceAll("\\{[^}]+}", "1");
            String resposta = mvc.perform(request(HttpMethod.valueOf(operacao.metodo().toUpperCase()), caminho))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            boolean pediuToken = resposta.contains("\"nao_autenticado\"");

            assertThat(pediuToken)
                    .as("%s documentada %s; sem token respondeu %s", operacao.nome(),
                            exigeToken ? "com token" : "sem token", resposta)
                    .isEqualTo(exigeToken);
            if (exigeToken) {
                assertThat(operacao.corpo().path("responses").has("401")).as(operacao.nome()).isTrue();
                assertThat(operacao.corpo().path("responses").has("403")).as(operacao.nome()).isTrue();
            }
        }
    }

    @Test
    void todoParametroETodoCampoDosModelosTemDescricao() {
        for (Operacao operacao : operacoes()) {
            operacao.corpo().path("parameters").valueStream().forEach(parametro -> assertThat(
                            parametro.path("description").asString(""))
                    .as(operacao.nome() + " parâmetro " + parametro.path("name").asString())
                    .isNotBlank());
        }
        documento.path("components").path("schemas").properties().forEach(modelo -> {
            assertThat(modelo.getValue().path("description").asString("")).as(modelo.getKey()).isNotBlank();
            modelo.getValue().path("properties").properties().forEach(campo -> {
                boolean referencia = campo.getValue().has("$ref") || campo.getValue().has("allOf");
                if (!referencia) {
                    assertThat(campo.getValue().path("description").asString(""))
                            .as(modelo.getKey() + "." + campo.getKey())
                            .isNotBlank();
                }
            });
        });
    }

    @Test
    void codigosDocumentadosExistemNoCodigoECodigosLancadosEstaoDocumentados() throws IOException {
        Set<String> documentados = new TreeSet<>();
        operacoes().forEach(operacao -> operacao.corpo().path("responses").properties()
                .forEach(resposta -> documentados.addAll(codigos(resposta.getValue().path("description").asString()))));
        String fontes;
        Set<String> lancados = new TreeSet<>();
        try (Stream<Path> arquivos = Files.walk(FONTES)) {
            StringBuilder todas = new StringBuilder();
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String texto = Files.readString(arquivo, StandardCharsets.UTF_8);
                todas.append(texto);
                Matcher lancado = CODIGO_LANCADO.matcher(texto);
                while (lancado.find()) {
                    lancados.add(lancado.group(1));
                }
            }
            fontes = todas.toString();
        }

        for (String codigo : documentados) {
            assertThat(fontes).as("código documentado que a API não usa: " + codigo).contains("\"" + codigo + "\"");
        }
        lancados.removeAll(CODIGOS_FORA_DAS_ROTAS);
        assertThat(documentados).as("códigos lançados e não documentados em nenhuma rota").containsAll(lancados);
    }
}
