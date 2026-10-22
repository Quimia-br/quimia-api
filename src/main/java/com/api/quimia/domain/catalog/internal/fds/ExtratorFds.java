package com.api.quimia.domain.catalog.internal.fds;

/**
 * Serviço externo (`quimia-extractor-ai`) que lê o PDF da FDS e devolve as seções 3, 4 e 10 em JSON. Contrato da
 * rota em `docs/plano-mestre/contratos/extrator-fds.md`.
 */
public interface ExtratorFds {
    /** JSON devolvido pelo extrator, sem validação. Lança {@link ExtratorFdsException} quando não há resposta útil. */
    String extrair(byte[] pdf);
}
