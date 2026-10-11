package com.api.quimia.domain.insights.internal.dto;

import java.util.List;

/** `tipo`: `numero` (usa `valor`) ou `rosca` (usa `series`). */
public record CardView(String id, String titulo, String tipo, Long valor, List<SerieItem> series) {}
