package com.api.quimia.domain.partner.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Ponto de compra ou descarte (`ponto_parceiro`). Só endereço: a tabela não tem coordenadas. `ativo` nulo vale ativo. */
@Entity
@Immutable
@Table(name = "ponto_parceiro")
public class PontoParceiro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_empresa")
    private Integer empresaId;

    @Column(nullable = false, length = 255)
    private String nome;

    @Column(length = 9)
    private String cep;

    @Column(length = 2)
    private String estado;

    @Column(length = 255)
    private String bairro;

    @Column(length = 255)
    private String rua;

    private Integer numero;

    @Column(length = 50)
    private String complemento;

    /** `compra` ou `descarte`. */
    @Column(length = 50)
    private String tipo;

    private Boolean ativo;

    public Integer getId() {
        return id;
    }

    public Integer getEmpresaId() {
        return empresaId;
    }

    public String getNome() {
        return nome;
    }

    public String getCep() {
        return cep;
    }

    public String getEstado() {
        return estado;
    }

    public String getBairro() {
        return bairro;
    }

    public String getRua() {
        return rua;
    }

    public Integer getNumero() {
        return numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public String getTipo() {
        return tipo;
    }

    public boolean isAtivo() {
        return !Boolean.FALSE.equals(ativo);
    }
}
