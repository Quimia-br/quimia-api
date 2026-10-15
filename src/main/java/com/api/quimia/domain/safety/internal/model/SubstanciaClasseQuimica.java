package com.api.quimia.domain.safety.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(
        name = "substancia_classe_quimica",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_substancia", "id_classe_quimica"}))
public class SubstanciaClasseQuimica {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_substancia", nullable = false)
    private Integer substanciaId;

    @Column(name = "id_classe_quimica", nullable = false)
    private Integer classeQuimicaId;

    public Integer getId() {
        return id;
    }

    public Integer getSubstanciaId() {
        return substanciaId;
    }

    public Integer getClasseQuimicaId() {
        return classeQuimicaId;
    }
}
