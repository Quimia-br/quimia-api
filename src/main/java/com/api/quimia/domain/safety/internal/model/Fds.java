package com.api.quimia.domain.safety.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/** FDS de um produto. `raw_json` não é mapeado: a API lê só o que o banco já normalizou. */
@Entity
@Immutable
@Table(name = "fds")
public class Fds {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_produto", nullable = false)
    private Integer produtoId;

    @Column(length = 20)
    private String versao;

    @Column(name = "data_atualizacao")
    private LocalDate dataAtualizacao;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "resumo_simplificado", columnDefinition = "text")
    private String resumoSimplificado;

    /** PDF guardado quando a FDS veio do portal; nulo nas FDS carregadas por outros meios. */
    @Column(name = "fonte_url", columnDefinition = "text")
    private String fonteUrl;

    public Integer getId() {
        return id;
    }

    public Integer getProdutoId() {
        return produtoId;
    }

    public String getVersao() {
        return versao;
    }

    public LocalDate getDataAtualizacao() {
        return dataAtualizacao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public String getResumoSimplificado() {
        return resumoSimplificado;
    }

    public String getFonteUrl() {
        return fonteUrl;
    }
}
