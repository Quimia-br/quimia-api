package com.api.quimia.domain.catalog.internal.persistence;

import com.api.quimia.domain.catalog.internal.model.EmpresaProduto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpresaProdutoRepository extends JpaRepository<EmpresaProduto, Integer> {
    Optional<EmpresaProduto> findByEmpresaIdAndProdutoId(Integer empresaId, Integer produtoId);

    /** Vínculos ativos do produto (`ativo` NULL vale como ativo). */
    @Query("select count(ep) from EmpresaProduto ep where ep.produtoId = :produto and (ep.ativo is null or ep.ativo = true)")
    long contarAtivos(@Param("produto") Integer produtoId);
}
