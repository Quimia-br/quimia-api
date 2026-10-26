package com.api.quimia.domain.account;

/** Armazenamento da foto de perfil do usuário, que fica fora do `account`. */
public interface FotoDoUsuarioGateway {
    /**
     * Apaga a foto no armazenamento. URL que não seja do armazenamento da API (ex.: a foto do Google) é ignorada,
     * e falha ao apagar não é propagada: a foto já foi trocada.
     */
    void apagar(String url);
}
