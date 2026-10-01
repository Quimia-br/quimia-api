package com.api.quimia.domain.account.internal.model;

import com.api.quimia.domain.account.NivelAcesso;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {
    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String nome;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "data_nasc")
    private LocalDate dataNasc;

    @Column(name = "foto_url", length = 450)
    private String fotoUrl;

    @Column(name = "senha", nullable = false, length = 100)
    private String senha;

    @Convert(converter = NivelAcessoConverter.class)
    @Column(name = "nivel_acesso", nullable = false, length = 50)
    private NivelAcesso nivelAcesso = NivelAcesso.USUARIO;

    @Column(name = "ultima_sessao")
    private OffsetDateTime ultimaSessao;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getDataNasc() {
        return dataNasc;
    }

    public void setDataNasc(LocalDate dataNasc) {
        this.dataNasc = dataNasc;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public NivelAcesso getNivelAcesso() {
        return nivelAcesso;
    }

    public void setNivelAcesso(NivelAcesso nivelAcesso) {
        this.nivelAcesso = nivelAcesso;
    }

    public OffsetDateTime getUltimaSessao() {
        return ultimaSessao;
    }

    public void setUltimaSessao(OffsetDateTime ultimaSessao) {
        this.ultimaSessao = ultimaSessao;
    }
}
