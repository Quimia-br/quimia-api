package com.api.quimia.domain.safety.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Só para gravar uma FDS recebida pelo portal, com o `raw_json` do extrator; o trigger do banco materializa
 * `fds_composto` e as demais tabelas a partir dele. A leitura usa {@link Fds}, que não traz o `raw_json`.
 */
@Entity(name = "NovaFds")
@Table(name = "fds")
public class NovaFds {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_produto", nullable = false)
    private Integer produtoId;

    @Column(name = "data_atualizacao")
    private LocalDate dataAtualizacao;

    @Column(nullable = false)
    private boolean ativo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json")
    private String rawJson;

    @Column(name = "fonte_url", columnDefinition = "text")
    private String fonteUrl;

    protected NovaFds() {}

    /**
     * FDS ativa recebida pelo portal: sem versão nem resumo (o extrator não os devolve), datada de hoje, com o PDF
     * guardado em `fonteUrl`.
     */
    public NovaFds(Integer produtoId, String rawJson, String fonteUrl, LocalDate hoje) {
        this.produtoId = produtoId;
        this.rawJson = rawJson;
        this.fonteUrl = fonteUrl;
        this.dataAtualizacao = hoje;
        this.ativo = true;
    }
}
