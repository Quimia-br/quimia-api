/**
 * Estante do usuário: {@code estante} e {@code estante_produto}.
 *
 * <p>Cada usuário tem uma única estante ({@code UNIQUE(estante.id_usuario)}), criada no primeiro
 * "Guardar". Dados de produto são lidos apenas pelo gateway público do módulo {@code catalog}.
 */
package com.api.quimia.domain.storage;
