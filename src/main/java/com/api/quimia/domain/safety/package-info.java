/**
 * Segurança química, somente leitura: {@code fds}, {@code fds_composto}, {@code substancia},
 * {@code substancia_sinonimo}, {@code substancia_classe_quimica} e {@code classe_quimica}.
 *
 * <p>Os dados chegam pelo parser da FDS e pela curadoria no banco; a API apenas consulta. A FDS
 * considerada de um produto é a ativa mais recente. Composto sem substância resolvida
 * ({@code id_substancia} nulo) não aparece nas listagens.
 */
package com.api.quimia.domain.safety;
