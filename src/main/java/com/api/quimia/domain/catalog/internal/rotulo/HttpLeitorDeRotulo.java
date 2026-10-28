package com.api.quimia.domain.catalog.internal.rotulo;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * `POST {url}/produtos/busca-por-imagem` do quimia-agent: multipart com o campo `imagem` →
 * `{candidatos: [{nome, marca, cod_barras}]}`. A imagem nunca vai para o log.
 */
public final class HttpLeitorDeRotulo implements LeitorDeRotulo {
    /** Maior corpo de resposta lido; acima disso a resposta é recusada sem carregar o resto. */
    static final int CORPO_MAXIMO = 256 * 1024;
    /** Candidatos acima deste total são ignorados: a tela do app não mostra mais que isso. */
    static final int CANDIDATOS_MAXIMOS = 10;

    private static final Duration CONEXAO = Duration.ofSeconds(3);
    private static final Logger log = LoggerFactory.getLogger(HttpLeitorDeRotulo.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient client;
    private final String url;
    private final String token;

    /** Cliente com HTTP bloqueante: o prazo vale para cada leitura e não depende do seletor NIO. */
    public static HttpLeitorDeRotulo criar(String url, String token, Duration prazoDeLeitura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(CONEXAO);
        fabrica.setReadTimeout(prazoDeLeitura);
        return new HttpLeitorDeRotulo(RestClient.builder().requestFactory(fabrica).build(), url, token);
    }

    /** `token` vazio: sem cabeçalho `Authorization` (o agente ainda não exige). */
    HttpLeitorDeRotulo(RestClient client, String url, String token) {
        this.client = client;
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.token = token;
    }

    @Override
    public List<CandidatoDoRotulo> identificar(byte[] imagem) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("imagem", new ByteArrayResource(imagem) {
            @Override
            public String getFilename() {
                return "rotulo";
            }
        });
        try {
            return client.post()
                    .uri(url + "/produtos/busca-por-imagem")
                    .headers(cabecalhos -> {
                        if (!token.isBlank()) {
                            cabecalhos.setBearerAuth(token);
                        }
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .exchange((pedido, resposta) -> candidatosOuFalha(resposta.getStatusCode(), resposta.getBody()));
        } catch (ResourceAccessException semResposta) {
            if (prazoEsgotado(semResposta)) {
                log.warn("agente não respondeu a busca por rótulo no prazo");
                throw new LeitorDeRotuloException(LeitorDeRotuloException.Motivo.TEMPO_ESGOTADO);
            }
            log.warn("agente sem conexão na busca por rótulo: {}", semResposta.getMessage());
            throw new LeitorDeRotuloException(LeitorDeRotuloException.Motivo.INDISPONIVEL);
        }
    }

    private static List<CandidatoDoRotulo> candidatosOuFalha(HttpStatusCode status, InputStream corpo)
            throws IOException {
        if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            throw new LeitorDeRotuloException(LeitorDeRotuloException.Motivo.INDISPONIVEL);
        }
        if (status.value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
            throw new LeitorDeRotuloException(LeitorDeRotuloException.Motivo.TEMPO_ESGOTADO);
        }
        if (!status.is2xxSuccessful()) {
            log.error("agente respondeu {} na busca por rótulo", status.value());
            throw new LeitorDeRotuloException(LeitorDeRotuloException.Motivo.FALHOU);
        }
        byte[] bytes = corpo.readNBytes(CORPO_MAXIMO + 1);
        if (bytes.length > CORPO_MAXIMO) {
            throw foraDoContrato("corpo acima de " + CORPO_MAXIMO + " bytes");
        }
        JsonNode raiz;
        try {
            raiz = JSON.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (JacksonException ilegivel) {
            throw foraDoContrato("JSON ilegível");
        }
        JsonNode candidatos = raiz.path("candidatos");
        if (!candidatos.isArray()) {
            throw foraDoContrato("sem a lista candidatos");
        }
        List<CandidatoDoRotulo> lidos = new ArrayList<>();
        for (JsonNode candidato : candidatos) {
            if (lidos.size() == CANDIDATOS_MAXIMOS) {
                break;
            }
            String nome = texto(candidato.path("nome"));
            if (nome == null) {
                throw foraDoContrato("candidato sem nome");
            }
            lidos.add(new CandidatoDoRotulo(nome, texto(candidato.path("marca")), texto(candidato.path("cod_barras"))));
        }
        return lidos;
    }

    /** Texto não vazio, ou `null` quando o campo falta, é nulo ou está em branco. */
    private static String texto(JsonNode campo) {
        return campo.isString() && !campo.asString().isBlank() ? campo.asString().strip() : null;
    }

    private static LeitorDeRotuloException foraDoContrato(String motivo) {
        log.error("resposta do agente na busca por rótulo fora do contrato: {}", motivo);
        return new LeitorDeRotuloException(LeitorDeRotuloException.Motivo.FALHOU);
    }

    private static boolean prazoEsgotado(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
