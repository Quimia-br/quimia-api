package com.api.quimia.infra.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Completa as respostas de erro de cada rota. Os controllers só listam, na descrição de cada resposta, os códigos entre
 * crases (`produto_nao_esta_na_estante`); aqui cada resposta 4xx/5xx ganha o corpo `application/problem+json` com um
 * exemplo por código, no lugar do corpo de sucesso que o springdoc deduz do retorno do método. Rotas protegidas ganham 401 e 403, e toda rota ganha 500.
 */
@Component
class RespostasDeErroDaApi implements GlobalOperationCustomizer {
    static final String PROBLEM_JSON = "application/problem+json";
    private static final Pattern CODIGO = Pattern.compile("`([a-z][a-z0-9_]*)`");
    private static final int PRIMEIRO_ERRO = 400;

    @Override
    public Operation customize(Operation operacao, HandlerMethod rota) {
        ApiResponses respostas = operacao.getResponses() == null ? new ApiResponses() : operacao.getResponses();
        boolean exigeToken = operacao.getSecurity() == null
                || operacao.getSecurity().stream().anyMatch(requisito -> requisito.containsKey(OpenApiConfig.BEARER));
        if (exigeToken) {
            garantir(respostas, 401, "Sem token, token expirado ou inválido: `nao_autenticado`.");
            garantir(respostas, 403, "Token de outro perfil (usuário na rota da empresa ou o contrário): `acesso_negado`.");
        }
        if (operacao.getRequestBody() != null) {
            if (temCorpo(operacao, "application/json")) {
                garantir(respostas, 400, "Corpo JSON malformado ou com tipo errado: `corpo_invalido`. Campo fora das "
                        + "regras: `validation` (lista em `errors`).");
                garantir(respostas, 415, "`Content-Type` diferente de `application/json`: `tipo_midia_nao_suportado`.");
            }
            if (temCorpo(operacao, "multipart/form-data")) {
                garantir(respostas, 400, "Parte obrigatória ausente: `parametro_obrigatorio`. Campo fora das regras: "
                        + "`validation`.");
                garantir(respostas, 413, "Pedido acima do limite do upload: `arquivo_muito_grande`.");
            }
        }
        if (operacao.getParameters() != null && operacao.getParameters().stream()
                .anyMatch(parametro -> "path".equals(parametro.getIn()) || "query".equals(parametro.getIn()))) {
            garantir(respostas, 400, "Parâmetro com tipo errado (ex.: texto onde se espera número): `parametro_invalido`.");
        }
        garantir(respostas, 500, "Erro inesperado na API: `erro_interno`. Os detalhes ficam só no log.");
        respostas.forEach((status, resposta) -> completar(status, resposta));
        operacao.setResponses(respostas);
        return operacao;
    }

    private static boolean temCorpo(Operation operacao, String tipo) {
        Content conteudo = operacao.getRequestBody().getContent();
        return conteudo != null && conteudo.containsKey(tipo);
    }

    /** Acrescenta a resposta, ou o código à descrição dela, quando a rota ainda não o lista. */
    private static void garantir(ApiResponses respostas, int status, String descricao) {
        String chave = String.valueOf(status);
        ApiResponse existente = respostas.get(chave);
        if (existente == null) {
            respostas.addApiResponse(chave, new ApiResponse().description(descricao));
            return;
        }
        Set<String> codigos = codigos(descricao);
        codigos.removeAll(codigos(existente.getDescription()));
        if (!codigos.isEmpty()) {
            existente.setDescription(existente.getDescription() + " " + descricao);
        }
    }

    private static void completar(String status, ApiResponse resposta) {
        int codigoHttp = Integer.parseInt(status);
        if (codigoHttp < PRIMEIRO_ERRO) {
            return;
        }
        Map<String, Example> exemplos = new LinkedHashMap<>();
        for (String codigo : codigos(resposta.getDescription())) {
            exemplos.put(codigo, new Example().value(Map.of(
                    "title", HttpStatus.valueOf(codigoHttp).getReasonPhrase(),
                    "status", codigoHttp,
                    "detail", codigo,
                    "instance", "/api/v1/...")));
        }
        MediaType corpo = new MediaType().schema(new Schema<>().$ref(OpenApiConfig.PROBLEMA_REF));
        if (!exemplos.isEmpty()) {
            corpo.setExamples(exemplos);
        }
        resposta.setContent(new Content().addMediaType(PROBLEM_JSON, corpo));
    }

    static Set<String> codigos(String descricao) {
        Set<String> codigos = new LinkedHashSet<>();
        if (descricao == null) {
            return codigos;
        }
        Matcher encontrado = CODIGO.matcher(descricao);
        while (encontrado.find()) {
            codigos.add(encontrado.group(1));
        }
        return codigos;
    }
}
