package com.api.quimia.domain.partner.internal.persistence;

import com.api.quimia.domain.partner.internal.model.PontoSalvo;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PontoSalvoRepository extends JpaRepository<PontoSalvo, Integer> {
    /** Retorna 1 quando salvou e 0 quando o ponto já estava salvo (a data original fica). */
    @Modifying
    @Query(value = """
            insert into ponto_salvo (id_usuario, id_ponto)
            values (:usuario, :ponto)
            on conflict do nothing
            """, nativeQuery = true)
    int salvar(@Param("usuario") UUID usuarioId, @Param("ponto") Integer pontoId);

    Optional<PontoSalvo> findByUsuarioIdAndPontoId(UUID usuarioId, Integer pontoId);

    /**
     * Pares (PontoSalvo, PontoParceiro) do mais recente ao mais antigo, só de pontos ativos e, se `tipo` vier, desse
     * tipo: a mesma regra de `buscar_pontos_salvos` do banco.
     */
    @Query(value = """
            select s, p from PontoSalvo s join PontoParceiro p on p.id = s.pontoId
            where s.usuarioId = :usuario
              and (:tipo is null or p.tipo = :tipo)
              and (p.ativo is null or p.ativo = true)
            order by s.criadoEm desc, s.id desc
            """,
            countQuery = """
            select count(s) from PontoSalvo s join PontoParceiro p on p.id = s.pontoId
            where s.usuarioId = :usuario
              and (:tipo is null or p.tipo = :tipo)
              and (p.ativo is null or p.ativo = true)
            """)
    Page<Object[]> doUsuario(@Param("usuario") UUID usuarioId, @Param("tipo") String tipo, Pageable pagina);

    @Modifying
    @Query("delete from PontoSalvo s where s.usuarioId = :usuario and s.pontoId = :ponto")
    int remover(@Param("usuario") UUID usuarioId, @Param("ponto") Integer pontoId);
}
