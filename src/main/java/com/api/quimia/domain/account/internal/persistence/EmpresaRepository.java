package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.internal.model.Empresa;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {
    /** `empresa.email` não é UNIQUE no schema; quem chama trata duplicidade legada. */
    @Query("select empresa from Empresa empresa where lower(empresa.email) = :email")
    List<Empresa> findAllByNormalizedEmail(@Param("email") String email);

    @Query("select count(empresa) > 0 from Empresa empresa where lower(empresa.email) = :email")
    boolean existsByNormalizedEmail(@Param("email") String email);

    boolean existsByCnpj(String cnpj);

    /** Só a coluna da foto, sem carregar a entidade: a gravação seguinte não regrava a linha inteira. */
    @Query("select empresa.fotoUrl from Empresa empresa where empresa.id = :id")
    Optional<String> findFotoUrlById(@Param("id") Integer id);

    /** Atualiza só `foto_url`; senha, ativo e demais colunas alteradas por outra transação ficam intactas. */
    @Modifying
    @Query("update Empresa empresa set empresa.fotoUrl = :fotoUrl where empresa.id = :id")
    int atualizarFotoUrl(@Param("id") Integer id, @Param("fotoUrl") String fotoUrl);
}
