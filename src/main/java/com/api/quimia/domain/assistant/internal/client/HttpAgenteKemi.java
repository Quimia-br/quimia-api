package com.api.quimia.domain.assistant.internal.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * `POST {url}/chat` do quimia-agent: `{session_id, user_id, pergunta}` → `{resposta}`. O conteúdo das mensagens
 * nunca vai para o log.
 */
public final class HttpAgenteKemi implements AgenteKemi {
    /** Maior corpo de resposta lido; acima disso a resposta é recusada sem carregar o resto. */
    static final int CORPO_MAXIMO = 256 * 1024;
    /** Maior resposta repassada ao app; uma resposta maior é tratada como falha do agente. */
    static final int RESPOSTA_MAXIMA = 20_000;

    private static final Duration CONEXAO = Duration.ofSeconds(3);
    private static final Logger log = LoggerFactory.getLogger(HttpAgenteKemi.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient client;
    private final String url;
    private final String token;

    /**
     * Cliente com HTTP bloqueante (`HttpURLConnection`): o prazo vale para cada leitura, inclusive no meio do corpo,
     * e não depende do seletor NIO.
     */
    public static HttpAgenteKemi criar(String url, String token, Duration prazoDeLeitura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(CONEXAO);
        fabrica.setReadTimeout(prazoDeLeitura);
        return new HttpAgenteKemi(RestClient.builder().requestFactory(fabrica).build(), url, token);
    }

    /** `token` vazio: sem cabeçalho `Authorization` (o agente ainda não exige). */
    HttpAgenteKemi(RestClient client, String url, String token) {
        this.client = client;
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.token = token;
    }

    @Override
    public String perguntar(UUID autorId, String sessaoId, String pergunta) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("session_id", sessaoId);
        corpo.put("user_id", autorId.toString());
        corpo.put("pergunta", pergunta);
        try {
            return client.post()
                    .uri(url + "/chat")
                    .headers(cabecalhos -> {
                        if (!token.isBlank()) {
                            cabecalhos.setBearerAuth(token);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(JSON.writeValueAsString(corpo))
                    .exchange((pedido, resposta) -> respostaOuFalha(resposta.getStatusCode(), resposta.getBody()));
        } catch (ResourceAccessException semResposta) {
            if (prazoEsgotado(semResposta)) {
                log.warn("agente Kemi não respondeu no prazo");
                throw new AgenteKemiException(AgenteKemiException.Motivo.TEMPO_ESGOTADO);
            }
            log.warn("agente Kemi sem conexão: {}", semResposta.getMessage());
            throw new AgenteKemiException(AgenteKemiException.Motivo.INDISPONIVEL);
        }
    }

    private static String respostaOuFalha(HttpStatusCode status, InputStream corpo) throws IOException {
        if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            throw new AgenteKemiException(AgenteKemiException.Motivo.INDISPONIVEL);
        }
        if (status.value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
            throw new AgenteKemiException(AgenteKemiException.Motivo.TEMPO_ESGOTADO);
        }
        if (!status.is2xxSuccessful()) {
            log.error("agente Kemi respondeu {}", status.value());
            throw new AgenteKemiException(AgenteKemiException.Motivo.FALHOU);
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
        JsonNode resposta = raiz.path("resposta");
        if (!resposta.isString() || resposta.asString().isBlank()) {
            throw foraDoContrato("sem o campo resposta");
        }
        String texto = resposta.asString().strip();
        if (texto.length() > RESPOSTA_MAXIMA) {
            throw foraDoContrato("resposta acima de " + RESPOSTA_MAXIMA + " caracteres");
        }
        return texto;
    }

    private static AgenteKemiException foraDoContrato(String motivo) {
        log.error("resposta do agente Kemi fora do contrato: {}", motivo);
        return new AgenteKemiException(AgenteKemiException.Motivo.FALHOU);
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
