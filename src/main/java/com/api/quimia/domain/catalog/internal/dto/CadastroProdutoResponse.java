package com.api.quimia.domain.catalog.internal.dto;

import com.api.quimia.domain.catalog.dto.ProdutoView;

/**
 * `situacao`: `criado` (produto novo), `vinculado` (o código de barras já existia no catálogo e a
 * empresa foi vinculada a ele, sem alterar seus dados) ou `reativado` (vínculo excluído antes).
 */
public record CadastroProdutoResponse(ProdutoView produto, String situacao) {}
