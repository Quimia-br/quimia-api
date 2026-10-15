package com.api.quimia.domain.storage.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** Estante única do usuário (`UNIQUE(id_usuario)`); criada no primeiro "Guardar". */
@Entity
@Table(name = "estante")
public class Estante {
    public static final String NOME_PADRAO = "Minha estante";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_usuario", nullable = false, unique = true)
    private UUID usuarioId;

    @Column(length = 255)
    private String nome;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    public Integer getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }
}
