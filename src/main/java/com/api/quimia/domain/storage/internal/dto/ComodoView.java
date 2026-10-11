package com.api.quimia.domain.storage.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import java.util.List;

public record ComodoView(Integer id, String nome, String icone, AvaliacaoComodo avaliacao, List<ProdutoView> produtos) {}
