package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.event.FdsRecebidaEvent;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFds;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFdsException;
import com.api.quimia.domain.catalog.internal.fds.ValidacaoDaExtracao;
import com.api.quimia.domain.catalog.internal.image.DocumentoStorage;
import com.api.quimia.domain.catalog.internal.image.ImageStorageException;
import jakarta.persistence.PersistenceException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF da FDS enviado pelo portal (campo `fds`). Fora da transação do cadastro, porque as duas etapas são lentas: o
 * PDF vai primeiro para o armazenamento (`fds.fonte_url`, para o download no detalhe do produto) e depois para o
 * extrator, que devolve o JSON gravado em `fds.raw_json`. Quem chama apaga o PDF ({@link #descartar}) se a gravação
 * não acontecer.
 */
@Component
public class FdsDoProduto {
    /** Maior arquivo `raw` que o Cloudinary aceita no plano da conta (10 MB). */
    static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(FdsDoProduto.class);
    private static final byte[] ASSINATURA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final String PASTA = "fds/";
    private static final String EXTENSAO = ".pdf";
    private static final String NOME_PADRAO = "fds.pdf";
    /** Limite do nome, sem a extensão, que aparece no fim da URL de download. */
    private static final int NOME_MAXIMO = 80;

    private final ExtratorFds extrator;
    private final DocumentoStorage documentos;
    private final ApplicationEventPublisher eventos;
    private final boolean obrigatoria;

    /** PDF validado e o nome com que será guardado (já seguro para URL). */
    record PdfDaFds(byte[] conteudo, String nomeArquivo) {}

    /** PDF já guardado (`url` na `pasta`) e a resposta validada do extrator. */
    record FdsPreparada(String url, String pasta, String json) {}

    public FdsDoProduto(
            ExtratorFds extrator,
            DocumentoStorage documentos,
            ApplicationEventPublisher eventos,
            @Value("${quimia.fds.obrigatoria:false}") boolean obrigatoria) {
        this.extrator = extrator;
        this.documentos = documentos;
        this.eventos = eventos;
        this.obrigatoria = obrigatoria;
    }

    /** Com a flag ligada, produto novo só é criado com FDS. */
    public boolean obrigatoria() {
        return obrigatoria;
    }

    /** PDF validado, ou `null` quando o formulário não trouxe arquivo. */
    PdfDaFds ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return null;
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new CatalogException("pdf_muito_grande", 413);
        }
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        }
        if (conteudo.length < ASSINATURA_PDF.length
                || !Arrays.equals(conteudo, 0, ASSINATURA_PDF.length, ASSINATURA_PDF, 0, ASSINATURA_PDF.length)) {
            throw new CatalogException("pdf_invalido", 415);
        }
        return new PdfDaFds(conteudo, nomeSeguro(arquivo.getOriginalFilename()));
    }

    /**
     * Guarda o PDF na pasta da empresa e o leva ao extrator. O armazenamento vem primeiro: se ele estiver fora do ar,
     * a resposta sai antes da chamada lenta ao extrator. Se a extração falhar, o PDF guardado é apagado.
     */
    FdsPreparada preparar(Integer empresaId, PdfDaFds pdf) {
        String pasta = PASTA + empresaId;
        String url = enviarPdf(pdf, pasta);
        try {
            return new FdsPreparada(url, pasta, extrair(pdf));
        } catch (RuntimeException falha) {
            documentos.apagarPdf(url, pasta);
            throw falha;
        }
    }

    /** Apaga o PDF de uma FDS preparada que não foi gravada (falha ou vínculo a produto existente). */
    void descartar(FdsPreparada preparada) {
        documentos.apagarPdf(preparada.url(), preparada.pasta());
    }

    /**
     * Grava a FDS como a ativa do produto, na transação corrente (o módulo de segurança química escuta o evento). Se o
     * banco ainda recusar o JSON (o trigger não trata exceção), a falha é do extrator, não do portal: responde 502 e a
     * transação inteira é desfeita.
     */
    void registrar(Integer produtoId, FdsPreparada preparada) {
        try {
            eventos.publishEvent(new FdsRecebidaEvent(produtoId, preparada.json(), preparada.url()));
        } catch (DataAccessException | PersistenceException recusada) {
            log.error("banco recusou a FDS extraída do produto {}", produtoId, recusada);
            throw new CatalogException("extrator_falhou", 502);
        }
    }

    /** JSON do extrator já conferido contra o que o banco aceita. */
    private String extrair(PdfDaFds pdf) {
        try {
            String resposta = extrator.extrair(pdf.conteudo());
            ValidacaoDaExtracao.validar(resposta);
            return resposta;
        } catch (ExtratorFdsException falha) {
            throw switch (falha.motivo()) {
                case INDISPONIVEL -> new CatalogException("extrator_indisponivel", 503);
                case FALHOU -> new CatalogException("extrator_falhou", 502);
                case DOCUMENTO_RECUSADO -> new CatalogException("fds_invalida", 422);
                case MUITO_GRANDE -> new CatalogException("pdf_muito_grande", 413);
            };
        }
    }

    private String enviarPdf(PdfDaFds pdf, String pasta) {
        try {
            return documentos.enviarPdf(pdf.conteudo(), pasta, pdf.nomeArquivo());
        } catch (ImageStorageException falha) {
            throw switch (falha.motivo()) {
                case INDISPONIVEL -> new CatalogException("armazenamento_fds_indisponivel", 503);
                case FALHOU -> new CatalogException("armazenamento_fds_falhou", 502);
            };
        }
    }

    /**
     * Nome enviado pelo portal reduzido ao que é seguro numa URL: sem caminho, sem acento, só letras, dígitos,
     * `.`, `_` e `-`, até {@value #NOME_MAXIMO} caracteres antes do `.pdf`.
     */
    static String nomeSeguro(String original) {
        if (original == null || original.isBlank()) {
            return NOME_PADRAO;
        }
        String semCaminho = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        String semExtensao = semCaminho.toLowerCase(Locale.ROOT).endsWith(EXTENSAO)
                ? semCaminho.substring(0, semCaminho.length() - EXTENSAO.length())
                : semCaminho;
        String base = semPontas(Normalizer.normalize(semExtensao, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-"));
        if (base.length() > NOME_MAXIMO) {
            base = semPontas(base.substring(0, NOME_MAXIMO));
        }
        return base.isEmpty() ? NOME_PADRAO : base + EXTENSAO;
    }

    private static String semPontas(String nome) {
        return nome.replaceAll("^[-.]+|[-.]+$", "");
    }
}
