package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.internal.model.Empresa;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {
    /** `empresa.email` não é UNIQUE no schema; quem chama trata duplicidade legada. */
    @Query("select empresa from Empresa empresa where lower(empresa.email) = :email")
    List<Empresa> findAllByNormalizedEmail(@Param("email") String email);

    @Query("select count(empresa) > 0 from Empresa empresa where lower(empresa.email) = :email")
    boolean existsByNormalizedEmail(@Param("email") String email);

    boolean existsByCnpj(String cnpj);
}
