package com.api.quimia.domain.storage.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Cômodo do usuário (`comodo`). O vínculo com os produtos fica em `estante_produto.id_comodo`. */
@Entity
@Table(name = "comodo")
public class Comodo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_usuario", nullable = false)
    private UUID usuarioId;

    @Column(length = 100)
    private String nome;

    /** Chave opaca do ícone escolhida no app. */
    @Column(name = "icone_comodo", length = 250)
    private String icone;

    public Integer getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getIcone() {
        return icone;
    }

    public void setIcone(String icone) {
        this.icone = icone;
    }
}
