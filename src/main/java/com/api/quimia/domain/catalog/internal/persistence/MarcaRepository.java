package com.api.quimia.domain.catalog.internal.persistence;

import com.api.quimia.domain.catalog.internal.model.Marca;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarcaRepository extends JpaRepository<Marca, Integer> {
    @Query("select m from Marca m where lower(m.nome) = lower(:nome) order by m.id")
    List<Marca> findByNomeSemCaixa(@Param("nome") String nome);

    Optional<Marca> findByNome(String nome);

    /** Idempotente e seguro com cadastros simultâneos: o UNIQUE(marca.nome) decide quem cria. */
    @Modifying
    @Query(value = "insert into marca (nome) values (:nome) on conflict do nothing", nativeQuery = true)
    int criarSeNaoExistir(@Param("nome") String nome);
}
