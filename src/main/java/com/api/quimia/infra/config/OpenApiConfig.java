package com.api.quimia.infra.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documento OpenAPI da API (`/v3/api-docs`, interface em `/swagger-ui.html`). As rotas e os DTOs se descrevem pelas
 * anotações do swagger-core; aqui ficam os dados gerais, os esquemas de autenticação, o modelo de erro e a ordem das
 * seções.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    /** Access token JWT do usuário do app ou da empresa do portal. */
    public static final String BEARER = "bearer";
    /** Cookie de renovação da empresa (portal web). */
    public static final String COOKIE_DE_RENOVACAO = "cookieDeRenovacao";
    /** Cabeçalho CSRF que acompanha o cookie de renovação. */
    public static final String CSRF = "csrf";
    /** Modelo dos erros (`application/problem+json`). */
    public static final String PROBLEMA = "Problema";
    public static final String PROBLEMA_REF = "#/components/schemas/" + PROBLEMA;

    private static final String DESCRICAO = """
            API do Quimia: app do consumidor (catálogo, estante, segurança química, cômodos, misturas, pontos e Kemi) \
            e portal das empresas (produtos, FDS, indicadores e WebKemi).

            **Autenticação.** Rotas do app exigem o access token de um **usuário**; rotas `/empresas/me/**` exigem o \
            de uma **empresa** (`Authorization: Bearer <token>`, válido por 15 minutos). Token de um perfil na rota \
            do outro responde 403 `acesso_negado`. No portal, a renovação usa o cookie `quimia_rt` com o cabeçalho \
            `X-CSRF-Token` igual ao cookie `quimia_csrf`.

            **Erros.** Todo erro sai em `application/problem+json` com `detail` igual a um código estável em \
            snake_case (por exemplo `produto_nao_esta_na_estante`). Os códigos possíveis de cada rota estão nas \
            respostas dela.

            **Paginação.** Listas paginadas recebem `page` (a partir de 0) e `size` (1 a 50) e devolvem `itens`, \
            `pagina`, `tamanho`, `totalItens` e `totalPaginas`.
            """;

    /** Ordem das seções na interface. */
    static final List<Tag> SECOES = List.of(
            secao("Usuário: conta", "Cadastro, login (senha e Google), perfil, endereço e sessão do app."),
            secao("Usuário: recuperação de senha", "Código de 4 dígitos por e-mail."),
            secao("Catálogo", "Produtos do app: lista, detalhe e busca por código de barras."),
            secao("Estante", "Produtos guardados pelo usuário."),
            secao("Segurança química", "Resumo do rótulo, substâncias, composição e primeiros socorros."),
            secao("Cômodos", "Agrupamento dos produtos da estante com avaliação de risco."),
            secao("Misturas", "Compatibilidade de dois produtos da estante e histórico."),
            secao("Pontos", "Pontos parceiros de compra e de descarte."),
            secao("Kemi", "Assistente do app."),
            secao("Empresa: conta", "Cadastro, login, sessão, perfil e logo da empresa."),
            secao("Empresa: recuperação de senha", "Link por e-mail."),
            secao("Empresa: produtos", "Produtos do portal, imagem e FDS."),
            secao("Empresa: indicadores", "Home e Estatísticas (só agregados)."),
            secao("Empresa: KPIs", "Gráficos de uso dos produtos da empresa (só agregados)."),
            secao("WebKemi", "Assistente do portal."));

    @Bean
    OpenAPI quimiaOpenApi(@Value("${app.auth.cookie-name:quimia_rt}") String cookieDeRenovacao) {
        return new OpenAPI()
                .info(new Info().title("Quimia API").version("v1").description(DESCRICAO))
                .tags(SECOES)
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token do login (`accessToken`)."))
                        .addSecuritySchemes(COOKIE_DE_RENOVACAO, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(cookieDeRenovacao)
                                .description("Refresh token da empresa, gravado pelo login como cookie HttpOnly."))
                        .addSecuritySchemes(CSRF, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-CSRF-Token")
                                .description("Valor do cookie `quimia_csrf`, exigido junto com o cookie de renovação."))
                        .addSchemas(PROBLEMA, problema()));
    }

    private static Tag secao(String nome, String descricao) {
        return new Tag().name(nome).description(descricao);
    }

    @SuppressWarnings("rawtypes")
    private static ObjectSchema problema() {
        ObjectSchema problema = new ObjectSchema();
        problema.description("Erro no formato RFC 9457 (`application/problem+json`).");
        problema.addProperty("title", new StringSchema().description("Nome do status HTTP.").example("Not Found"));
        problema.addProperty("status", new IntegerSchema().description("Status HTTP.").example(404));
        problema.addProperty("detail", new StringSchema()
                .description("Código estável do erro, em snake_case. É o campo para o front decidir o que mostrar.")
                .example("produto_nao_esta_na_estante"));
        problema.addProperty("instance", new StringSchema()
                .description("Caminho da requisição.").example("/api/v1/estante/produtos/42/resumo-simplificado"));
        problema.addProperty("errors", new ArraySchema()
                .items(new StringSchema())
                .description("Só em `validation`: um item por campo inválido, no formato `campo: mensagem`.")
                .example(List.of("email: must be a well-formed email address")));
        problema.required(List.of("title", "status", "detail", "instance"));
        return problema;
    }
}
