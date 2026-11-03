package com.api.quimia.domain.catalog.internal.fds;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Extração em duas etapas, porque o host do extrator corta qualquer resposta após cerca de 30 s e uma FDS real pode
 * levar mais: `POST {url}/extracoes/assincronas` com o PDF no campo `arquivo` e o token no `Authorization` devolve um
 * `id` na hora, e `GET {url}/extracoes/assincronas/{id}` é consultado até a extração terminar ou o prazo acabar.
 */
public final class HttpExtratorFds implements ExtratorFds {
    static final Duration INTERVALO_PADRAO = Duration.ofSeconds(2);
    private static final Logger log = LoggerFactory.getLogger(HttpExtratorFds.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NOME_DO_ARQUIVO = "fds.pdf";
    private static final String ROTA = "/extracoes/assincronas";
    private static final String CONCLUIDA = "concluida";
    private static final String FALHOU = "falhou";
    private static final String PROCESSANDO = "processando";
    private static final int TRECHO_DO_CORPO_NO_LOG = 200;

    private final RestClient client;
    private final String url;
    private final String token;
    private final Duration intervalo;
    private final Duration prazo;

    /** Resposta já lida por inteiro; `tipo` e o início do corpo vão ao log quando o formato não é o esperado. */
    private record Resposta(int status, String tipo, String corpo) {}

    public HttpExtratorFds(RestClient client, String url, String token) {
        this(client, url, token, INTERVALO_PADRAO, Duration.ofSeconds(60));
    }

    /** `prazo` é o tempo total para a extração terminar, somando o envio e todas as consultas. */
    public HttpExtratorFds(RestClient client, String url, String token, Duration intervalo, Duration prazo) {
        this.client = client;
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.token = token;
        this.intervalo = intervalo;
        this.prazo = prazo;
    }

    @Override
    public String extrair(byte[] pdf) {
        long limite = System.nanoTime() + prazo.toNanos();
        String id = iniciar(pdf);
        while (true) {
            JsonNode estado = consultar(id);
            String situacao = estado.path("status").asString();
            if (CONCLUIDA.equals(situacao)) {
                return JSON.writeValueAsString(estado.path("resultado"));
            }
            if (FALHOU.equals(situacao)) {
                throw falhaDaExtracao(estado.path("codigo").asInt());
            }
            if (!PROCESSANDO.equals(situacao)) {
                log.error("extrator de FDS devolveu o estado '{}' para a extração {}", situacao, id);
                throw new ExtratorFdsException(ExtratorFdsException.Motivo.FALHOU);
            }
            if (System.nanoTime() >= limite) {
                log.warn("extrator de FDS não terminou a extração {} em {} s", id, prazo.toSeconds());
                throw new ExtratorFdsException(ExtratorFdsException.Motivo.INDISPONIVEL);
            }
            aguardar();
        }
    }

    private String iniciar(byte[] pdf) {
        Resposta resposta = chamar(() -> client.post()
                .uri(url + ROTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .body(formulario(pdf))
                .exchange((pedido, recebida) -> lida(recebida.getStatusCode().value(), recebida.getHeaders(),
                        recebida.getBody().readAllBytes())));
        if (resposta.status() < 200 || resposta.status() >= 300) {
            throw falhaDoEnvio(resposta);
        }
        String id = corpoEmJson(resposta).path("id").asString();
        if (id.isBlank()) {
            log.error("extrator de FDS aceitou o envio sem devolver o id: {}", descrever(resposta));
            throw new ExtratorFdsException(ExtratorFdsException.Motivo.FALHOU);
        }
        return id;
    }

    private JsonNode consultar(String id) {
        Resposta resposta = chamar(() -> client.get()
                .uri(url + ROTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .exchange((pedido, recebida) -> lida(recebida.getStatusCode().value(), recebida.getHeaders(),
                        recebida.getBody().readAllBytes())));
        if (resposta.status() != HttpStatus.OK.value()) {
            log.error("consulta da extração {} no extrator de FDS: {}", id, descrever(resposta));
            throw new ExtratorFdsException(ExtratorFdsException.Motivo.FALHOU);
        }
        return corpoEmJson(resposta);
    }

    private static Resposta chamar(Supplier<Resposta> chamada) {
        try {
            return chamada.get();
        } catch (ResourceAccessException semResposta) {
            log.warn("extrator de FDS sem resposta: {}", semResposta.getMessage());
            throw new ExtratorFdsException(ExtratorFdsException.Motivo.INDISPONIVEL);
        }
    }

    private static Resposta lida(int status, HttpHeaders cabecalhos, byte[] corpo) {
        return new Resposta(status, String.valueOf(cabecalhos.getContentType()), new String(corpo, StandardCharsets.UTF_8));
    }

    /** O corpo como JSON, ou FALHOU com status, Content-Type e início do corpo no log (ex.: a página HTML do host). */
    private static JsonNode corpoEmJson(Resposta resposta) {
        try {
            return JSON.readTree(resposta.corpo());
        } catch (JacksonException ilegivel) {
            log.error("extrator de FDS devolveu corpo que não é JSON: {}", descrever(resposta));
            throw new ExtratorFdsException(ExtratorFdsException.Motivo.FALHOU);
        }
    }

    private static String descrever(Resposta resposta) {
        String corpo = resposta.corpo();
        String trecho = corpo.length() > TRECHO_DO_CORPO_NO_LOG ? corpo.substring(0, TRECHO_DO_CORPO_NO_LOG) : corpo;
        return "status=" + resposta.status() + " tipo=" + resposta.tipo() + " corpo=" + trecho.replaceAll("\\s+", " ");
    }

    private static ExtratorFdsException falhaDoEnvio(Resposta resposta) {
        int status = resposta.status();
        if (status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
            return new ExtratorFdsException(ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        }
        if (status == HttpStatus.CONTENT_TOO_LARGE.value()) {
            return new ExtratorFdsException(ExtratorFdsException.Motivo.MUITO_GRANDE);
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value() || status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            log.warn("extrator de FDS ocupado ou sem configuração: {}", descrever(resposta));
            return new ExtratorFdsException(ExtratorFdsException.Motivo.INDISPONIVEL);
        }
        log.error("extrator de FDS recusou o envio: {}", descrever(resposta));
        return new ExtratorFdsException(ExtratorFdsException.Motivo.FALHOU);
    }

    /** O `codigo` é o status que a rota síncrona do extrator devolveria para a mesma falha. */
    private static ExtratorFdsException falhaDaExtracao(int codigo) {
        if (codigo == HttpStatus.BAD_REQUEST.value() || codigo == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
            return new ExtratorFdsException(ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        }
        if (codigo == HttpStatus.CONTENT_TOO_LARGE.value()) {
            return new ExtratorFdsException(ExtratorFdsException.Motivo.MUITO_GRANDE);
        }
        log.error("extração do extrator de FDS terminou com o código {}", codigo);
        return new ExtratorFdsException(ExtratorFdsException.Motivo.FALHOU);
    }

    private void aguardar() {
        try {
            Thread.sleep(intervalo);
        } catch (InterruptedException interrompida) {
            Thread.currentThread().interrupt();
            throw new ExtratorFdsException(ExtratorFdsException.Motivo.INDISPONIVEL);
        }
    }

    private static MultiValueMap<String, Object> formulario(byte[] pdf) {
        HttpHeaders cabecalho = new HttpHeaders();
        cabecalho.setContentType(MediaType.APPLICATION_PDF);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("arquivo", new HttpEntity<>(new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return NOME_DO_ARQUIVO;
            }
        }, cabecalho));
        return form;
    }
}
