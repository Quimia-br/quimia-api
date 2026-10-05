package com.api.quimia.domain.catalog.internal.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CloudinaryImageStorageTest {
    private static final String CLOUD = "quimia-teste";
    private static final String KEY = "chave-publica";
    private static final String SECRET = "segredo-de-teste";
    private static final long AGORA = 1_700_000_000L;
    private static final String FIM_LINHA = "\r\n";
    private static final String FIM_CABECALHO = FIM_LINHA + FIM_LINHA;
    private static final String BASE = "https://api.cloudinary.com/v1_1/" + CLOUD;
    private static final String URL_DA_CONTA =
            "https://res.cloudinary.com/" + CLOUD + "/image/upload/v1700000000/produtos/abc-123.png";

    private MockRestServiceServer servidor;
    private CloudinaryImageStorage storage;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        Clock relogio = Clock.fixed(Instant.ofEpochSecond(AGORA), ZoneOffset.UTC);
        storage = new CloudinaryImageStorage(builder.build(), relogio, CLOUD, KEY, SECRET);
    }

    @Test
    void enviaAssinadoComPublicIdGeradoPelaApi() {
        AtomicReference<String> corpo = new AtomicReference<>();
        servidor.expect(requestTo(BASE + "/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> corpo.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"secure_url\":\"" + URL_DA_CONTA + "\"}", MediaType.APPLICATION_JSON));

        String url = storage.upload(new byte[] {1, 2, 3});

        assertThat(url).isEqualTo(URL_DA_CONTA);
        String publicId = campo(corpo.get(), "public_id");
        assertThat(publicId).matches("produtos/[0-9a-f-]{36}");
        assertThat(campo(corpo.get(), "timestamp")).isEqualTo(String.valueOf(AGORA));
        assertThat(campo(corpo.get(), "api_key")).isEqualTo(KEY);
        assertThat(campo(corpo.get(), "signature")).isEqualTo(sha1("public_id=" + publicId + "&timestamp=" + AGORA + SECRET));
        assertThat(corpo.get()).doesNotContain(SECRET);
        servidor.verify();
    }

    @Test
    void falhaDoServicoViraBadGatewaySemVazarDetalhes() {
        servidor.expect(requestTo(BASE + "/image/upload")).andRespond(withServerError());

        assertThatThrownBy(() -> storage.upload(new byte[] {1}))
                .isInstanceOfSatisfying(ImageStorageException.class, erro -> {
                    assertThat(erro.motivo()).isEqualTo(ImageStorageException.Motivo.FALHOU);
                });
    }

    @Test
    void respostaSemUrlEhFalha() {
        servidor.expect(requestTo(BASE + "/image/upload")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> storage.upload(new byte[] {1}))
                .isInstanceOfSatisfying(ImageStorageException.class, erro -> assertThat(erro.motivo()).isEqualTo(ImageStorageException.Motivo.FALHOU));
    }

    @Test
    void apagaPeloPublicIdExtraidoDaUrl() {
        servidor.expect(requestTo(BASE + "/image/destroy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("produtos/abc-123")))
                .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));

        storage.delete(URL_DA_CONTA);

        servidor.verify();
    }

    @Test
    void naoApagaUrlDeOutraContaForaDaPastaOuNula() {
        storage.delete(null);
        storage.delete("https://res.cloudinary.com/outra-conta/image/upload/v1/produtos/abc.png");
        storage.delete("https://res.cloudinary.com/" + CLOUD + "/image/upload/v1/logos/marca.png");
        storage.delete("https://exemplo.com/foto.png");

        servidor.verify();
    }

    @Test
    void falhaAoApagarNaoPropagaExcecao() {
        servidor.expect(requestTo(BASE + "/image/destroy")).andRespond(withServerError());

        storage.delete(URL_DA_CONTA);

        servidor.verify();
    }

    @Test
    void semVariaveisOArmazenamentoFicaIndisponivel() {
        ImageStorage desativado = new ImageStorageConfiguration().imageStorage("", "", "");

        assertThatThrownBy(() -> desativado.upload(new byte[] {1}))
                .isInstanceOfSatisfying(ImageStorageException.class, erro -> {
                    assertThat(erro.motivo()).isEqualTo(ImageStorageException.Motivo.INDISPONIVEL);
                });
        desativado.delete(URL_DA_CONTA);
    }

    private static String campo(String multipart, String nome) {
        String marca = "name=\"" + nome + "\"";
        int inicio = multipart.indexOf(marca);
        assertThat(inicio).as("campo %s no multipart", nome).isGreaterThanOrEqualTo(0);
        int valor = multipart.indexOf(FIM_CABECALHO, inicio) + FIM_CABECALHO.length();
        return multipart.substring(valor, multipart.indexOf(FIM_LINHA, valor));
    }

    private static String sha1(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ausente) {
            throw new IllegalStateException(ausente);
        }
    }
}
