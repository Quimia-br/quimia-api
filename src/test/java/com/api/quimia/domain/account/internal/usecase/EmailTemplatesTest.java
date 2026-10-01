package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplatesTest {
    private final EmailTemplates templates = new EmailTemplates();

    @Test
    void recoveryCodeTemplateShowsCodeAndFifteenMinuteValidity() {
        String html = templates.render(EmailTemplates.RECOVERY_CODE, Map.of("code", "4821"));

        assertThat(html).contains("4821", "apenas 15 minutos", "Não compartilhe").doesNotContain("{{");
    }

    @Test
    void recoveryLinkTemplateEscapesTheLink() {
        String html = templates.render(
                EmailTemplates.RECOVERY_LINK, Map.of("link", "https://portal.example.com/r?token=a.b&x=<y>"));

        assertThat(html)
                .contains("href=\"https://portal.example.com/r?token=a.b&amp;x=&lt;y&gt;\"")
                .doesNotContain("<y>", "{{");
    }

    @Test
    void templatesReferenceTheEmbeddedPngLogo() {
        byte[] logo = templates.logo();

        assertThat(logo).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(templates.render(EmailTemplates.RECOVERY_CODE, Map.of("code", "1234")))
                .contains("src=\"cid:" + EmailTemplates.LOGO_CONTENT_ID + "\"");
        assertThat(templates.render(EmailTemplates.RECOVERY_LINK, Map.of("link", "https://x.example/r")))
                .contains("src=\"cid:" + EmailTemplates.LOGO_CONTENT_ID + "\"");
    }

    @Test
    void missingValuesFailInsteadOfSendingPlaceholders() {
        assertThatThrownBy(() -> templates.render(EmailTemplates.RECOVERY_CODE, Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
