package com.api.quimia.domain.safety.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Seção 4 (primeiros socorros) da FDS ativa mais recente, para o "Como se proteger?" do app. `encontrada` falso:
 * produto sem FDS, FDS sem a seção ou conteúdo ilegível; as listas vêm vazias. `porExposicao` segue a ordem
 * inalação, pele, olhos, ingestão.
 */
public record PrimeirosSocorrosView(
        Integer produtoId,
        boolean encontrada,
        List<PrimeiroSocorroView> porExposicao,
        List<String> orientacoesGerais,
        List<String> notasAoMedico,
        String versaoFds,
        LocalDate dataAtualizacaoFds) {

    /** `rotaExposicao`: `inalacao`, `pele`, `olhos` ou `ingestao`. */
    public record PrimeiroSocorroView(
            String rotaExposicao,
            String descricao,
            String sintomas,
            String tratamentoEspecial,
            boolean atencaoMedicaImediata) {}
}
