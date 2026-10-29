package com.api.quimia.domain.partner.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;

/** Ponto parceiro salvo pelo usuário (`ponto_salvo`); o par (usuário, ponto) é único. */
@Entity
@Table(name = "ponto_salvo", uniqueConstraints = @UniqueConstraint(columnNames = {"id_usuario", "id_ponto"}))
public class PontoSalvo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_usuario", nullable = false)
    private UUID usuarioId;

    @Column(name = "id_ponto", nullable = false)
    private Integer pontoId;

    /** Preenchida pelo banco ao salvar; ordena a lista e define o último salvo. */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    public Integer getPontoId() {
        return pontoId;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
