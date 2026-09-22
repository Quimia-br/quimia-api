package com.api.quimia.domain.account.internal.model;

import com.api.quimia.domain.account.NivelAcesso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_acesso", nullable = false, length = 50)
    private NivelAcesso nivelAcesso = NivelAcesso.USUARIO;

    @Column(name = "ultima_sessao")
    private OffsetDateTime ultimaSessao;

    @Column(name = "senha_hash", length = 255)
    private String senhaHash;

    @Column(name = "email_verificado_em")
    private OffsetDateTime emailVerificadoEm;

    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "verification_expira_em")
    private OffsetDateTime verificationExpiraEm;

    @Column(name = "falhas_login", nullable = false)
    private int falhasLogin;

    @Column(name = "bloqueado_ate")
    private OffsetDateTime bloqueadoAte;

    @Column(name = "ultima_falha_em")
    private OffsetDateTime ultimaFalhaEm;

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

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public OffsetDateTime getEmailVerificadoEm() {
        return emailVerificadoEm;
    }

    public void setEmailVerificadoEm(OffsetDateTime emailVerificadoEm) {
        this.emailVerificadoEm = emailVerificadoEm;
    }

    public String getVerificationTokenHash() {
        return verificationTokenHash;
    }

    public void setVerificationTokenHash(String verificationTokenHash) {
        this.verificationTokenHash = verificationTokenHash;
    }

    public OffsetDateTime getVerificationExpiraEm() {
        return verificationExpiraEm;
    }

    public void setVerificationExpiraEm(OffsetDateTime verificationExpiraEm) {
        this.verificationExpiraEm = verificationExpiraEm;
    }

    public int getFalhasLogin() {
        return falhasLogin;
    }

    public void setFalhasLogin(int falhasLogin) {
        this.falhasLogin = falhasLogin;
    }

    public OffsetDateTime getBloqueadoAte() {
        return bloqueadoAte;
    }

    public void setBloqueadoAte(OffsetDateTime bloqueadoAte) {
        this.bloqueadoAte = bloqueadoAte;
    }

    public OffsetDateTime getUltimaFalhaEm() {
        return ultimaFalhaEm;
    }

    public void setUltimaFalhaEm(OffsetDateTime ultimaFalhaEm) {
        this.ultimaFalhaEm = ultimaFalhaEm;
    }
}
