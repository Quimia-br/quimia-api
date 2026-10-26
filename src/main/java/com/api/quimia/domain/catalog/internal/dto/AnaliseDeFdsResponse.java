package com.api.quimia.domain.catalog.internal.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Leitura da FDS para a empresa revisar antes de cadastrar. Nada foi gravado: o `analiseId` (assinado, pode ter dezenas
 * de KB) leva a leitura até a confirmação do cadastro e vai sempre no corpo, nunca na URL. `status` é `processado` ou
 * `processado_com_pendencias`; `mensagem` é o texto do popup quando há pendências.
 */
public record AnaliseDeFdsResponse(
        String analiseId,
        OffsetDateTime expiraEm,
        String status,
        String mensagem,
        List<PendenciaDaFds> pendencias,
        List<CompostoDaFds> compostos) {

    /**
     * Pendência da leitura. `motivo` é um de secao_nao_localizada, informacao_ausente, documento_nao_especifica,
     * termo_ambiguo, dados_contraditorios ou evidencia_invalida. `composto` aponta o composto a que ela se refere
     * (nulo quando é do documento ou da seção). `corrigivel` indica que a empresa pode preencher o valor em
     * `correcoesFds` ao confirmar (hoje, a unidade de concentração de um composto que veio sem ela); as demais são
     * avisos, e a empresa só confirma.
     */
    public record PendenciaDaFds(
            String secao,
            String campo,
            String motivo,
            String descricao,
            ReferenciaDoComposto composto,
            boolean corrigivel,
            List<EvidenciaDaFds> evidencias) {}

    /** Composto da seção 3 a que uma pendência se refere; `indice` é a posição em `compostos`. */
    public record ReferenciaDoComposto(int indice, String nome, String casNumero) {}

    /** Trecho do documento que sustenta a pendência. */
    public record EvidenciaDaFds(String secao, Integer pagina, String textoEvidencia) {}

    /** Composto lido na seção 3; `unidadeConcentracao` é nula quando o documento não a informa. */
    public record CompostoDaFds(
            int indice,
            String nome,
            String casNumero,
            BigDecimal concentracaoMin,
            BigDecimal concentracaoMax,
            String unidadeConcentracao) {}
}
