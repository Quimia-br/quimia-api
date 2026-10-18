package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.event.FdsRecebidaEvent;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFds;
import com.api.quimia.domain.catalog.internal.fds.ExtratorFdsException;
import com.api.quimia.domain.catalog.internal.fds.ValidacaoDaExtracao;
import jakarta.persistence.PersistenceException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF da FDS enviado pelo portal (campo `fds`): valida o arquivo e o leva ao extrator, que devolve o JSON gravado
 * em `fds.raw_json`. A chamada ao extrator é lenta e acontece fora da transação do cadastro.
 */
@Component
public class FdsDoProduto {
    static final long TAMANHO_MAXIMO = 15L * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(FdsDoProduto.class);
    private static final byte[] ASSINATURA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ExtratorFds extrator;
    private final ApplicationEventPublisher eventos;
    private final boolean obrigatoria;

    public FdsDoProduto(
            ExtratorFds extrator,
            ApplicationEventPublisher eventos,
            @Value("${quimia.fds.obrigatoria:false}") boolean obrigatoria) {
        this.extrator = extrator;
        this.eventos = eventos;
        this.obrigatoria = obrigatoria;
    }

    /** Com a flag ligada, produto novo só é criado com FDS. */
    public boolean obrigatoria() {
        return obrigatoria;
    }

    /** Bytes do PDF validado, ou `null` quando o formulário não trouxe arquivo. */
    public byte[] ler(MultipartFile arquivo) {
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
        return conteudo;
    }

    /**
     * Grava a FDS extraída como a ativa do produto, na transação corrente (o módulo de segurança química escuta o
     * evento). Se o banco ainda recusar o JSON (o trigger não trata exceção), a falha é do extrator, não do portal:
     * responde 502 e a transação inteira é desfeita.
     */
    public void registrar(Integer produtoId, String fdsExtraida) {
        try {
            eventos.publishEvent(new FdsRecebidaEvent(produtoId, fdsExtraida));
        } catch (DataAccessException | PersistenceException recusada) {
            log.error("banco recusou a FDS extraída do produto {}", produtoId, recusada);
            throw new CatalogException("extrator_falhou", 502);
        }
    }

    /** JSON do extrator já conferido contra o que o banco aceita. */
    public String extrair(byte[] pdf) {
        try {
            String resposta = extrator.extrair(pdf);
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
}
