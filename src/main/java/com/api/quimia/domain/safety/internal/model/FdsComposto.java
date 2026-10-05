package com.api.quimia.domain.safety.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "fds_composto")
public class FdsComposto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_fds", nullable = false)
    private Integer fdsId;

    /** Nulo quando o parser não resolveu a substância (fica como pendência de curadoria). */
    @Column(name = "id_substancia")
    private Integer substanciaId;

    @Column(name = "concentracao_min", precision = 5, scale = 2)
    private BigDecimal concentracaoMin;

    @Column(name = "concentracao_max", precision = 5, scale = 2)
    private BigDecimal concentracaoMax;

    @Column(name = "unidade_concentracao", length = 20)
    private String unidadeConcentracao;

    public Integer getId() {
        return id;
    }

    public Integer getFdsId() {
        return fdsId;
    }

    public Integer getSubstanciaId() {
        return substanciaId;
    }

    public BigDecimal getConcentracaoMin() {
        return concentracaoMin;
    }

    public BigDecimal getConcentracaoMax() {
        return concentracaoMax;
    }

    public String getUnidadeConcentracao() {
        return unidadeConcentracao;
    }
}
