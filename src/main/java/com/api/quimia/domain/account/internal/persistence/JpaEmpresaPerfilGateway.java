package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.EmpresaPerfilGateway;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * `@Component` e não `@Repository`: a tradução de exceções do `@Repository` converteria a
 * `IllegalArgumentException` do contrato em `InvalidDataAccessApiUsageException`.
 *
 * Lê e grava só a coluna `foto_url`, sem carregar `Empresa`: quem chama mantém a transação aberta durante o upload
 * da imagem, e uma entidade carregada antes dele regravaria no commit a senha e o `ativo` antigos.
 */
@Component
public class JpaEmpresaPerfilGateway implements EmpresaPerfilGateway {
    private final EmpresaRepository empresas;

    public JpaEmpresaPerfilGateway(EmpresaRepository empresas) {
        this.empresas = empresas;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> fotoAtual(Integer empresaId) {
        return empresas.findFotoUrlById(empresaId);
    }

    @Override
    @Transactional
    public void atualizarFoto(Integer empresaId, String fotoUrl) {
        if (empresas.atualizarFotoUrl(empresaId, fotoUrl) == 0) {
            throw new IllegalArgumentException("empresa_nao_encontrada");
        }
    }
}
