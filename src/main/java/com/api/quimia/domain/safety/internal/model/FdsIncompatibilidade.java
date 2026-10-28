package com.api.quimia.domain.safety.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Incompatibilidade declarada na seção 10 de uma FDS. O banco (`fn_processar_fds_raw_json`) resolve o reagente para uma
 * substância ou uma classe química; o que não resolveu fica só como texto (`substanciaReagente`) e não entra no motor.
 */
@Entity
@Immutable
@Table(name = "fds_incompatibilidade")
public class FdsIncompatibilidade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_fds", nullable = false)
    private Integer fdsId;

    @Column(name = "id_substancia")
    private Integer substanciaId;

    @Column(name = "id_classe_quimica")
    private Integer classeQuimicaId;

    @Column(name = "substancia_reagente", length = 255)
    private String substanciaReagente;

    @Column(name = "descricao_risco")
    private String descricaoRisco;

    @Column(length = 20)
    private String severidade;

    public Integer getId() {
        return id;
    }

    public Integer getFdsId() {
        return fdsId;
    }

    public Integer getSubstanciaId() {
        return substanciaId;
    }

    public Integer getClasseQuimicaId() {
        return classeQuimicaId;
    }

    public String getSubstanciaReagente() {
        return substanciaReagente;
    }

    public String getDescricaoRisco() {
        return descricaoRisco;
    }

    public String getSeveridade() {
        return severidade;
    }
}
