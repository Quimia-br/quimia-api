package com.api.quimia.domain.account;

import com.api.quimia.domain.account.dto.LocalizacaoView;
import java.util.Optional;
import java.util.UUID;

/** Endereço cadastrado do usuário, para módulos que filtram por local. Vazio quando não há cadastro. */
public interface LocalizacaoGateway {
    Optional<LocalizacaoView> doUsuario(UUID usuarioId);
}
