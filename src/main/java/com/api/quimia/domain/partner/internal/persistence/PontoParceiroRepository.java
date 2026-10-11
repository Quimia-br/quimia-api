package com.api.quimia.domain.partner.internal.persistence;

import com.api.quimia.domain.partner.internal.model.PontoParceiro;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PontoParceiroRepository extends JpaRepository<PontoParceiro, Integer> {
    /** `bairro` chega já em minúsculas e sem acento; `ativo` nulo vale ativo. */
    @Query("""
            select p from PontoParceiro p
            where p.tipo = :tipo
              and (p.ativo is null or p.ativo = true)
              and (:estado is null or p.estado = :estado)
              and (:bairro is null or lower(unaccent(p.bairro)) = :bairro)
            """)
    List<PontoParceiro> buscar(
            @Param("tipo") String tipo, @Param("estado") String estado, @Param("bairro") String bairro);
}
