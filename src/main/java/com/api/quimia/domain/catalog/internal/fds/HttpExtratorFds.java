package com.api.quimia.domain.catalog.internal.fds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** `POST {url}/extracoes` com o PDF no campo `arquivo` e o token no `Authorization`. */
public final class HttpExtratorFds implements ExtratorFds {
    private static final Logger log = LoggerFactory.getLogger(HttpExtratorFds.class);
    private static final String NOME_DO_ARQUIVO = "fds.pdf";

    private final RestClient client;
    private final String url;
    private final String token;

    public HttpExtratorFds(RestClient client, String url, String token) {
        this.client = client;
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.token = token;
    }

    @Override
    public String extrair(byte[] pdf) {
        try {
            return client.post()
                    .uri(url + "/extracoes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formulario(pdf))
                    .exchange((pedido, resposta) -> corpoOuFalha(resposta.getStatusCode(), resposta.getBody().readAllBytes()));
        } catch (ResourceAccessException semResposta) {
            log.warn("extrator de FDS sem resposta: {}", semResposta.getMessage());
            throw new ExtratorFdsException(ExtratorFdsException.Motivo.INDISPONIVEL);
        }
    }

    private static String corpoOuFalha(HttpStatusCode status, byte[] corpo) throws IOException {
        if (status.is2xxSuccessful()) {
            return new String(corpo, StandardCharsets.UTF_8);
        }
        if (status.value() == HttpStatus.BAD_REQUEST.value() || status.value() == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
            throw new ExtratorFdsException(ExtratorFdsException.Motivo.DOCUMENTO_RECUSADO);
        }
        if (status.value() == HttpStatus.CONTENT_TOO_LARGE.value()) {
            throw new ExtratorFdsException(ExtratorFdsException.Motivo.MUITO_GRANDE);
        }
        log.error("extrator de FDS respondeu {}", status.value());
        throw new ExtratorFdsException(ExtratorFdsException.Motivo.FALHOU);
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
