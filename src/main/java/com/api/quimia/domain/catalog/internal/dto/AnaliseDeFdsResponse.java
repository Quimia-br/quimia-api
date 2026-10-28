package com.api.quimia.domain.catalog.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Leitura da FDS para a empresa revisar antes de cadastrar. Nada foi gravado: o `analiseId` (assinado, pode ter dezenas
 * de KB) leva a leitura até a confirmação do cadastro e vai sempre no corpo, nunca na URL.
 */
@Schema(description = "Resultado da leitura da FDS, para a empresa revisar as pendências antes de cadastrar o produto.")
public record AnaliseDeFdsResponse(
        @Schema(description = "Identificador assinado da análise; vai em `analiseFds` no cadastro ou na alteração do "
                + "produto. Pode ter dezenas de KB: envie no corpo, nunca na URL.")
        String analiseId,
        @Schema(description = "Até quando o `analiseId` vale (30 minutos por padrão).",
                example = "2026-10-28T10:15:30Z")
        OffsetDateTime expiraEm,
        @Schema(description = "processado (sem pendências) ou processado_com_pendencias.",
                example = "processado_com_pendencias")
        String status,
        @Schema(description = "Texto para o popup quando há pendências.",
                example = "Informações ausentes, verifique o documento anexado")
        String mensagem,
        @Schema(description = "O que a IA não conseguiu confirmar no documento.")
        List<PendenciaDaFds> pendencias,
        @Schema(description = "Compostos lidos na seção 3, na ordem em que `indice` os referencia.")
        List<CompostoDaFds> compostos) {

    /**
     * Pendência da leitura. `motivo` é um de secao_nao_localizada, informacao_ausente, documento_nao_especifica,
     * termo_ambiguo, dados_contraditorios ou evidencia_invalida.
     */
    @Schema(description = "Pendência da leitura. `corrigivel` indica que a empresa pode preencher o valor ao confirmar.")
    public record PendenciaDaFds(
            @Schema(description = "Seção da FDS: 03, 04 ou 10.", example = "03") String secao,
            @Schema(description = "Campo a que a pendência se refere.", example = "unidade_concentracao") String campo,
            @Schema(description = "secao_nao_localizada, informacao_ausente, documento_nao_especifica, "
                    + "termo_ambiguo, dados_contraditorios ou evidencia_invalida.", example = "informacao_ausente")
            String motivo,
            @Schema(description = "O que faltou ou ficou ambíguo.",
                    example = "A faixa de concentração é informada como 2 – 2,5, mas a unidade de concentração não "
                            + "está especificada.")
            String descricao,
            @Schema(description = "Composto a que a pendência se refere; ausente quando ela é do documento ou da seção.")
            ReferenciaDoComposto composto,
            @Schema(description = "true quando a empresa pode informar o valor em `correcoesFds` (hoje, a unidade de "
                    + "concentração de um composto que veio sem ela). As demais são avisos: a empresa só confirma.")
            boolean corrigivel,
            @Schema(description = "Trechos do documento que sustentam a pendência.")
            List<EvidenciaDaFds> evidencias) {}

    @Schema(description = "Composto da seção 3 a que uma pendência se refere.")
    public record ReferenciaDoComposto(
            @Schema(description = "Posição em `compostos`.", example = "0") int indice,
            @Schema(description = "Nome do composto.", example = "Hipoclorito de Sódio") String nome,
            @Schema(description = "Número CAS, se informado.", example = "7681-52-9") String casNumero) {}

    @Schema(description = "Trecho do documento que sustenta a pendência.")
    public record EvidenciaDaFds(
            @Schema(description = "Seção da FDS.", example = "03") String secao,
            @Schema(description = "Página do PDF.", example = "2") Integer pagina,
            @Schema(description = "Citação literal do documento.", example = "Hipoclorito de Sódio 7681-52-9 2 – 2,5 NaClO")
            String textoEvidencia) {}

    @Schema(description = "Composto lido na seção 3. A unidade vem vazia quando o documento não a informa.")
    public record CompostoDaFds(
            @Schema(description = "Posição na lista, referenciada por `correcoesFds`.", example = "0") int indice,
            @Schema(description = "Nome do composto.", example = "Hipoclorito de Sódio") String nome,
            @Schema(description = "Número CAS, se informado.", example = "7681-52-9") String casNumero,
            @Schema(description = "Concentração mínima.", example = "2") BigDecimal concentracaoMin,
            @Schema(description = "Concentração máxima.", example = "2.5") BigDecimal concentracaoMax,
            @Schema(description = "Unidade da concentração; nula quando o documento não a informa.", example = "%")
            String unidadeConcentracao) {}
}
