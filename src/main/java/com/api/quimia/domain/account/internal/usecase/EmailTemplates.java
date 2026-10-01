package com.api.quimia.domain.account.internal.usecase;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Templates HTML de email em `templates/email/`, carregados na inicialização. Valores são escapados.
 * A logo vai como imagem embutida (`cid:`): o Gmail descarta SVG e imagens em data URI.
 */
@Component
public class EmailTemplates {
    public static final String LOGO_CONTENT_ID = "quimia-logo";
    public static final String LOGO_FILENAME = "quimia-logo.png";
    static final String RECOVERY_CODE = "recuperacao-codigo";
    static final String RECOVERY_LINK = "recuperacao-link";
    private static final String TEMPLATE_DIR = "templates/email/";

    private final Map<String, String> templates;
    private final byte[] logo;

    public EmailTemplates() {
        this.templates = Map.of(
                RECOVERY_CODE, new String(load(RECOVERY_CODE + ".html"), StandardCharsets.UTF_8),
                RECOVERY_LINK, new String(load(RECOVERY_LINK + ".html"), StandardCharsets.UTF_8));
        this.logo = load(LOGO_FILENAME);
    }

    /** PNG da logo referenciada como `cid:quimia-logo` nos templates. */
    public byte[] logo() {
        return logo.clone();
    }

    public String render(String name, Map<String, String> values) {
        String html = templates.get(name);
        if (html == null) {
            throw new IllegalArgumentException("Unknown email template " + name);
        }
        for (Map.Entry<String, String> value : values.entrySet()) {
            html = html.replace("{{" + value.getKey() + "}}", HtmlUtils.htmlEscape(value.getValue()));
        }
        if (html.contains("{{")) {
            throw new IllegalStateException("Unresolved placeholder in email template " + name);
        }
        return html;
    }

    private static byte[] load(String fileName) {
        try (InputStream input = new ClassPathResource(TEMPLATE_DIR + fileName).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException error) {
            throw new UncheckedIOException("Email resource not found: " + fileName, error);
        }
    }
}
