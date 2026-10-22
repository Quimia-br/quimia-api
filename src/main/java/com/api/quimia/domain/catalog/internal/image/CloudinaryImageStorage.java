package com.api.quimia.domain.catalog.internal.image;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cloudinary pela API REST com requisição assinada (SHA-1 dos parâmetros + segredo), sem SDK e sem
 * upload preset aberto. O `public_id` é gerado aqui (`{pasta}/{uuid}`), nunca vem do cliente. Imagens vão como
 * recurso `image`; os PDFs das FDS, como `raw` (`{pasta}/{uuid}/{nome}.pdf`, para o download sair com o nome).
 */
public final class CloudinaryImageStorage implements ArmazenamentoDeArquivos {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);
    private static final String API = "https://api.cloudinary.com/v1_1/";
    private static final String IMAGEM = "image";
    private static final String DOCUMENTO = "raw";
    private static final ParameterizedTypeReference<Map<String, Object>> RESPOSTA =
            new ParameterizedTypeReference<>() {};

    private final RestClient imagens;
    private final RestClient documentos;
    private final Clock clock;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    /** `documentos` tem prazo maior: o PDF da FDS chega a 10 MB, a imagem a 5 MB. */
    public CloudinaryImageStorage(
            RestClient imagens, RestClient documentos, Clock clock, String cloudName, String apiKey, String apiSecret) {
        this.imagens = imagens;
        this.documentos = documentos;
        this.clock = clock;
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    @Override
    public String upload(byte[] conteudo, String pasta) {
        return enviar(IMAGEM, pasta + "/" + UUID.randomUUID(), "imagem", conteudo);
    }

    @Override
    public void delete(String url, String pasta) {
        apagar(IMAGEM, publicIdDe(url, pasta));
    }

    @Override
    public String enviarPdf(byte[] conteudo, String pasta, String nomeArquivo) {
        return enviar(DOCUMENTO, pasta + "/" + UUID.randomUUID() + "/" + nomeArquivo, nomeArquivo, conteudo);
    }

    @Override
    public void apagarPdf(String url, String pasta) {
        apagar(DOCUMENTO, publicIdDoPdf(url, pasta));
    }

    /** Só reconhece imagens desta conta diretamente dentro de `pasta`; qualquer outra URL é ignorada. */
    String publicIdDe(String url, String pasta) {
        if (url == null || !url.contains("/" + cloudName + "/image/upload/")) {
            return null;
        }
        Pattern publicIdNaPasta =
                Pattern.compile("/image/upload/(?:v\\d+/)?(" + Pattern.quote(pasta + "/") + "[^/.]+)\\.?[A-Za-z0-9]*$");
        Matcher achou = publicIdNaPasta.matcher(url);
        return achou.find() ? achou.group(1) : null;
    }

    /** O `public_id` de um recurso `raw` inclui a extensão: `{pasta}/{uuid}/{nome}.pdf`. */
    String publicIdDoPdf(String url, String pasta) {
        if (url == null || !url.contains("/" + cloudName + "/raw/upload/")) {
            return null;
        }
        Pattern publicIdNaPasta = Pattern.compile(
                "/raw/upload/(?:v\\d+/)?(" + Pattern.quote(pasta + "/") + "[0-9a-f-]{36}/[A-Za-z0-9._-]+\\.pdf)$");
        Matcher achou = publicIdNaPasta.matcher(url);
        return achou.find() ? achou.group(1) : null;
    }

    private String enviar(String tipoDeRecurso, String publicId, String nomeDoArquivo, byte[] conteudo) {
        MultiValueMap<String, Object> form = assinado(publicId);
        form.add("file", new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nomeDoArquivo;
            }
        });
        Map<String, Object> resposta;
        try {
            resposta = clienteDe(tipoDeRecurso).post()
                    .uri(API + cloudName + "/" + tipoDeRecurso + "/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(RESPOSTA);
        } catch (RestClientException falha) {
            log.warn("cloudinary upload failed: {}", falha.getMessage());
            throw new ImageStorageException(ImageStorageException.Motivo.FALHOU);
        }
        Object url = resposta == null ? null : resposta.get("secure_url");
        if (!(url instanceof String texto) || texto.isBlank()) {
            throw new ImageStorageException(ImageStorageException.Motivo.FALHOU);
        }
        return texto;
    }

    private void apagar(String tipoDeRecurso, String publicId) {
        if (publicId == null) {
            return;
        }
        try {
            clienteDe(tipoDeRecurso).post()
                    .uri(API + cloudName + "/" + tipoDeRecurso + "/destroy")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(assinado(publicId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException falha) {
            // a compensação não pode derrubar a requisição; o aviso permite limpar o arquivo órfão depois
            log.warn("cloudinary delete failed for {}: {}", publicId, falha.getMessage());
        }
    }

    private RestClient clienteDe(String tipoDeRecurso) {
        return DOCUMENTO.equals(tipoDeRecurso) ? documentos : imagens;
    }

    private MultiValueMap<String, Object> assinado(String publicId) {
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("public_id", publicId);
        form.add("timestamp", timestamp);
        form.add("api_key", apiKey);
        form.add("signature", assinatura("public_id=" + publicId + "&timestamp=" + timestamp));
        return form;
    }

    private String assinatura(String parametrosOrdenados) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest((parametrosOrdenados + apiSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ausente) {
            throw new IllegalStateException("SHA-1 indisponível", ausente);
        }
    }
}
