package com.api.quimia.domain.account;

import java.util.UUID;

/** Foto de perfil do usuário do app, gravada por quem guarda a imagem. */
public interface UsuarioPerfilGateway {
    /**
     * Grava a URL (até 450 caracteres) e apaga a foto anterior depois do commit, se nenhum outro usuário a usa.
     * Usuário inexistente lança {@code IllegalArgumentException("usuario_nao_encontrado")}.
     */
    void atualizarFoto(UUID usuarioId, String fotoUrl);
}
