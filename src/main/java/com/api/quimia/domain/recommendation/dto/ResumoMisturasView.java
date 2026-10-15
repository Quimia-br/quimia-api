package com.api.quimia.domain.recommendation.dto;

/** Contagem de misturas por resultado: `compativel`, `incompativel` e `nao_avaliado`. */
public record ResumoMisturasView(long total, long compativeis, long incompativeis, long naoAvaliadas) {}
