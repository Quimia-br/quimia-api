package com.api.quimia.infra.persistence;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registra no HQL a função `unaccent` da extensão homônima do PostgreSQL (criada pelo DDL do
 * quimia-bd), para buscas que ignoram acentos. Carregada via ServiceLoader
 * (META-INF/services/org.hibernate.boot.model.FunctionContributor).
 */
public class QuimiaFunctionContributor implements FunctionContributor {
    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions.getFunctionRegistry().registerPattern(
                "unaccent",
                "unaccent(?1)",
                contributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING));
    }
}
