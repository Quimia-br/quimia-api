/**
 * KPIs do portal da empresa. Sem tabelas próprias: lê pelo JDBC as views do data mart ({@code vw_fato_historico_recomendacao},
 * {@code dim_usuario}) filtradas pelos produtos ativos da empresa em {@code empresa_produto}. A empresa vem sempre da
 * identidade autenticada; nunca de URL, corpo ou query string.
 */
package com.api.quimia.domain.kpi;
