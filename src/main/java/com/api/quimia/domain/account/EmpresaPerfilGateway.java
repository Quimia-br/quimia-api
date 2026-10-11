package com.api.quimia.domain.account;

import java.util.Optional;

/** Foto da empresa do portal, gravada por quem guarda a imagem. */
public interface EmpresaPerfilGateway {
    /** URL atual da foto; vazio quando a empresa não tem foto ou não existe. */
    Optional<String> fotoAtual(Integer empresaId);

    /** Grava a URL (até 200 caracteres). Empresa inexistente lança {@code IllegalArgumentException("empresa_nao_encontrada")}. */
    void atualizarFoto(Integer empresaId, String fotoUrl);
}
