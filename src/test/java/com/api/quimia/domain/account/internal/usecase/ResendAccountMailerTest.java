package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Modo `resend`: o que vai para a API do Resend e quando a aplicação se recusa a subir. */
class ResendAccountMailerTest {
    private static final String CHAVE = "re_chave_de_teste";
    private static final String REMETENTE = "Quimia <nao-responda@quimia.app>";
    private static final byte[] LOGO = {1, 2, 3, 4};

    private MockRestServiceServer resend;
    private RecoveryEmailConfiguration.ResendAccountMailer mailer;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        resend = MockRestServiceServer.bindTo(builder).build();
        mailer = new RecoveryEmailConfiguration.ResendAccountMailer(builder.build(), CHAVE, REMETENTE, LOGO);
    }

    @Test
    void enviaRemetenteDestinatarioTextoHtmlELogoEmbutido() {
        AtomicReference<String> corpo = new AtomicReference<>();
        resend.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + CHAVE))
                .andExpect(request -> corpo.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"id\":\"email-1\"}", MediaType.APPLICATION_JSON));

        boolean entregue = mailer.send("pessoa@example.com", "Código de recuperação", "Seu código é 1234.", "<p>1234</p>");

        resend.verify();
        JsonNode json = JsonMapper.builder().build().readTree(corpo.get());
        assertThat(entregue).isTrue();
        assertThat(json.get("from").asText()).isEqualTo(REMETENTE);
        assertThat(json.get("to").get(0).asText()).isEqualTo("pessoa@example.com");
        assertThat(json.get("subject").asText()).isEqualTo("Código de recuperação");
        assertThat(json.get("text").asText()).isEqualTo("Seu código é 1234.");
        assertThat(json.get("html").asText()).isEqualTo("<p>1234</p>");
        JsonNode logo = json.get("attachments").get(0);
        assertThat(logo.get("content_id").asText()).isEqualTo(EmailTemplates.LOGO_CONTENT_ID);
        assertThat(logo.get("filename").asText()).isEqualTo(EmailTemplates.LOGO_FILENAME);
        assertThat(logo.get("content").asText()).isEqualTo(Base64.getEncoder().encodeToString(LOGO));
    }

    /** O listener transforma a exceção em auditoria `password_recovery_email_failed`; o erro não pode sumir. */
    @Test
    void recusaDoResendViraExcecao() {
        resend.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> mailer.send("pessoa@example.com", "s", "t", "<p>t</p>"))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void naoSobeSemChaveOuSemRemetente() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(RecoveryEmailConfiguration.class, EmailTemplates.class);

        runner.withPropertyValues("app.email.sender-mode=resend", "app.email.from=" + REMETENTE)
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("RESEND_API_KEY")
                        .hasMessageNotContaining("APP_EMAIL_FROM"));
        runner.withPropertyValues("app.email.sender-mode=resend", "app.email.resend.api-key=" + CHAVE)
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("APP_EMAIL_FROM")
                        .hasMessageNotContaining("RESEND_API_KEY"));
    }
}
