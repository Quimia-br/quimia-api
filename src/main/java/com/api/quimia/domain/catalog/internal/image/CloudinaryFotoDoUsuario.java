package com.api.quimia.domain.catalog.internal.image;

import com.api.quimia.domain.account.FotoDoUsuarioGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apaga a foto antiga do perfil. O app envia a foto direto ao Cloudinary, na pasta de perfil, depois que o
 * usuário confirma; a API só a apaga quando ela é trocada. Só o que está nessa pasta é apagado: `PATCH /usuarios/me`
 * aceita qualquer URL https e não autoriza apagar imagem alheia.
 */
@Component
class CloudinaryFotoDoUsuario implements FotoDoUsuarioGateway {
    private final ImageStorage storage;
    private final String pasta;

    CloudinaryFotoDoUsuario(ImageStorage storage, @Value("${quimia.cloudinary.pasta-perfil:quimia/perfil}") String pasta) {
        this.storage = storage;
        this.pasta = pasta;
    }

    @Override
    public void apagar(String url) {
        storage.delete(url, pasta);
    }
}
