package com.api.quimia.domain.recommendation.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consulta de mistura de um usuário. Cada lado é um produto (`id_produto_*`) ou uma substância (`id_substancia_*`),
 * nunca os dois. O par é normalizado: produto antes de substância e, no mesmo tipo, o menor id primeiro. O banco exige
 * `resultado` em `compativel`, `incompativel` ou `nao_avaliado`.
 */
@Entity
@Table(name = "historico_match")
public class HistoricoMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_usuario", nullable = false)
    private UUID usuarioId;

    @Column(name = "id_produto_a")
    private Integer produtoAId;

    @Column(name = "id_produto_b")
    private Integer produtoBId;

    @Column(name = "id_substancia_a")
    private Integer substanciaAId;

    @Column(name = "id_substancia_b")
    private Integer substanciaBId;

    @Column(nullable = false, length = 20)
    private String resultado;

    @Column(name = "id_regra")
    private Integer regraId;

    @Column(length = 20)
    private String severidade;

    @Column(name = "descricao_risco", columnDefinition = "text")
    private String descricaoRisco;

    @Column(name = "data_consulta", nullable = false)
    private OffsetDateTime dataConsulta;

    public Integer getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Integer getProdutoAId() {
        return produtoAId;
    }

    public void setProdutoAId(Integer produtoAId) {
        this.produtoAId = produtoAId;
    }

    public Integer getProdutoBId() {
        return produtoBId;
    }

    public void setProdutoBId(Integer produtoBId) {
        this.produtoBId = produtoBId;
    }

    public Integer getSubstanciaAId() {
        return substanciaAId;
    }

    public void setSubstanciaAId(Integer substanciaAId) {
        this.substanciaAId = substanciaAId;
    }

    public Integer getSubstanciaBId() {
        return substanciaBId;
    }

    public void setSubstanciaBId(Integer substanciaBId) {
        this.substanciaBId = substanciaBId;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public Integer getRegraId() {
        return regraId;
    }

    public void setRegraId(Integer regraId) {
        this.regraId = regraId;
    }

    public String getSeveridade() {
        return severidade;
    }

    public void setSeveridade(String severidade) {
        this.severidade = severidade;
    }

    public String getDescricaoRisco() {
        return descricaoRisco;
    }

    public void setDescricaoRisco(String descricaoRisco) {
        this.descricaoRisco = descricaoRisco;
    }

    public OffsetDateTime getDataConsulta() {
        return dataConsulta;
    }

    public void setDataConsulta(OffsetDateTime dataConsulta) {
        this.dataConsulta = dataConsulta;
    }
}
