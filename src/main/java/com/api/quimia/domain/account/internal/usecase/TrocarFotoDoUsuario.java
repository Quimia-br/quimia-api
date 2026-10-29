package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.UsuarioPerfilGateway;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lê e grava só a coluna `foto_url`, sem carregar `Usuario`: quem chama mantém a transação aberta durante o upload da
 * imagem, e uma entidade carregada antes dele regravaria no commit os campos antigos.
 */
@Component
class TrocarFotoDoUsuario implements UsuarioPerfilGateway {
    private final UsuarioRepository users;
    private final FotoAnteriorDoUsuario fotoDescartada;

    TrocarFotoDoUsuario(UsuarioRepository users, FotoAnteriorDoUsuario fotoDescartada) {
        this.users = users;
        this.fotoDescartada = fotoDescartada;
    }

    @Override
    @Transactional
    public void atualizarFoto(UUID usuarioId, String fotoUrl) {
        String anterior = users.findFotoUrlById(usuarioId).orElse(null);
        if (users.atualizarFotoUrl(usuarioId, fotoUrl) == 0) {
            throw new IllegalArgumentException("usuario_nao_encontrado");
        }
        fotoDescartada.apagarAposCommit(usuarioId, anterior, fotoUrl);
    }
}
