package com.api.quimia.domain.safety.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Regra curada de incompatibilidade. Cada lado é uma substância ou uma classe química (o banco
 * garante que exatamente um dos dois esteja preenchido).
 */
@Entity
@Immutable
@Table(name = "incompatibilidade_regra")
public class IncompatibilidadeRegra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_substancia_a")
    private Integer substanciaAId;

    @Column(name = "id_classe_a")
    private Integer classeAId;

    @Column(name = "id_substancia_b")
    private Integer substanciaBId;

    @Column(name = "id_classe_b")
    private Integer classeBId;

    @Column(nullable = false, length = 20)
    private String severidade;

    @Column(name = "descricao_risco", nullable = false, columnDefinition = "text")
    private String descricaoRisco;

    @Column(columnDefinition = "text")
    private String fonte;

    @Column(nullable = false)
    private boolean ativo;

    public Integer getId() {
        return id;
    }

    public Integer getSubstanciaAId() {
        return substanciaAId;
    }

    public Integer getClasseAId() {
        return classeAId;
    }

    public Integer getSubstanciaBId() {
        return substanciaBId;
    }

    public Integer getClasseBId() {
        return classeBId;
    }

    public String getSeveridade() {
        return severidade;
    }

    public String getDescricaoRisco() {
        return descricaoRisco;
    }

    public String getFonte() {
        return fonte;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
