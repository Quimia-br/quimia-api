package com.api.quimia.domain.safety.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "substancia_sinonimo")
public class SubstanciaSinonimo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_substancia", nullable = false)
    private Integer substanciaId;

    @Column(nullable = false, length = 255)
    private String sinonimo;

    @Column(name = "sinonimo_normalizado", nullable = false, length = 255, unique = true)
    private String sinonimoNormalizado;

    public Integer getId() {
        return id;
    }

    public Integer getSubstanciaId() {
        return substanciaId;
    }

    public String getSinonimo() {
        return sinonimo;
    }
}
