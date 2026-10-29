package com.api.quimia.domain.storage.internal.model;

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

/**
 * Produto guardado na estante. `id_usuario` repete o dono da estante; o trigger
 * `trg_validar_estante_produto` do banco garante que coincidam.
 */
@Entity
@Table(name = "estante_produto", uniqueConstraints = @UniqueConstraint(columnNames = {"id_estante", "id_produto"}))
public class EstanteProduto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_produto", nullable = false)
    private Integer produtoId;

    @Column(name = "id_usuario", nullable = false)
    private UUID usuarioId;

    @Column(name = "id_estante", nullable = false)
    private Integer estanteId;

    /** Cômodo do produto; um produto pertence a no máximo um cômodo. */
    @Column(name = "id_comodo")
    private Integer comodoId;

    /** Preenchida pelo banco ao guardar; ordena a estante por "recentes" e "antigos". */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "data_adicionado", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataAdicionado;

    /** Começa falso (default do banco); só muda pelas rotas de favorito. */
    @ColumnDefault("false")
    @Column(nullable = false, insertable = false)
    private boolean favorito;

    public Integer getProdutoId() {
        return produtoId;
    }

    public Integer getComodoId() {
        return comodoId;
    }

    public void setComodoId(Integer comodoId) {
        this.comodoId = comodoId;
    }
}
