package com.api.quimia.domain.safety.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * O JSON do extrator guardado em `fds.raw_json`, só para leitura. Separado de {@link Fds} para que as consultas de
 * FDS não carreguem o documento inteiro.
 */
@Entity
@Immutable
@Table(name = "fds")
public class FdsConteudo {
    @Id
    private Integer id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", insertable = false, updatable = false)
    private String rawJson;

    public Integer getId() {
        return id;
    }

    public String getRawJson() {
        return rawJson;
    }
}
