package com.api.quimia.domain.insights.internal.dto;

/** `taxaSucesso` = bemSucedidas / (bemSucedidas + malSucedidas), 4 casas; nula sem denominador. */
public record ResumoMisturas(long total, long bemSucedidas, long malSucedidas, long naoAvaliadas, Double taxaSucesso) {}
