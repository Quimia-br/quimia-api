package com.api.quimia.domain.catalog.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;
import java.util.List;

/** `naoIdentificados`: quantos produtos lidos no rótulo não casaram com nenhum produto do catálogo. */
public record BuscaPorImagemView(List<ProdutoView> produtos, int naoIdentificados) {}
