package com.api.quimia.domain.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * Seção 4 (primeiros socorros) da FDS ativa mais recente, para o "Como se proteger?" do app. `encontrada` falso:
 * produto sem FDS, FDS sem a seção ou conteúdo ilegível; as listas vêm vazias. `porExposicao` segue a ordem
 * inalação, pele, olhos, ingestão.
 */
@Schema(description = "Seção 4 da FDS (primeiros socorros).")
public record PrimeirosSocorrosView(
        @Schema(description = "Id do produto.", example = "42")
        Integer produtoId,
        @Schema(description = "Falso sem FDS, sem a seção ou com conteúdo fora do contrato.", example = "true")
        boolean encontrada,
        @Schema(description = "Um registro por rota de exposição, na ordem inalacao, pele, olhos, ingestao.")
        List<PrimeiroSocorroView> porExposicao,
        @Schema(description = "Orientações gerais.")
        List<String> orientacoesGerais,
        @Schema(description = "Notas ao médico.")
        List<String> notasAoMedico,
        @Schema(description = "Versão da FDS lida.", example = "3")
        String versaoFds,
        @Schema(description = "Data da FDS lida.", example = "2026-01-15")
        LocalDate dataAtualizacaoFds) {

    /** `rotaExposicao`: `inalacao`, `pele`, `olhos` ou `ingestao`. */
    @Schema(description = "Primeiros socorros para uma rota de exposição.")
    public record PrimeiroSocorroView(
            @Schema(description = "inalacao, pele, olhos ou ingestao.", example = "olhos")
            String rotaExposicao,
            @Schema(description = "O que fazer.", example = "Lavar com água corrente por 15 minutos.")
            String descricao,
            @Schema(description = "Sintomas.", example = "Vermelhidão e ardor.")
            String sintomas,
            @Schema(description = "Tratamento especial.", example = "Procurar oftalmologista.")
            String tratamentoEspecial,
            @Schema(description = "Exige atenção médica imediata.", example = "true")
            boolean atencaoMedicaImediata) {}
}
