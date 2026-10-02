/**
 * Catálogo de produtos: {@code marca}, {@code produto} e {@code empresa_produto}.
 *
 * <p>O app lê o catálogo compartilhado; a empresa mantém, pelo portal, os produtos aos quais
 * está vinculada. Excluir no portal desativa o vínculo ({@code empresa_produto.ativo = false}),
 * nunca apaga o produto. A tabela {@code empresa} pertence ao módulo {@code account}: aqui só
 * se usa o id da empresa autenticada.
 */
package com.api.quimia.domain.catalog;
