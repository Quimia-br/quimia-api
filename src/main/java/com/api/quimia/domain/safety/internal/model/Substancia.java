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
@Table(name = "substancia")
public class Substancia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nome_canonico", nullable = false, length = 255)
    private String nomeCanonico;

    @Column(name = "cas_numero", length = 20, unique = true)
    private String casNumero;

    @Column(columnDefinition = "text")
    private String descricao;

    public Integer getId() {
        return id;
    }

    public String getNomeCanonico() {
        return nomeCanonico;
    }

    public String getCasNumero() {
        return casNumero;
    }

    public String getDescricao() {
        return descricao;
    }
}
