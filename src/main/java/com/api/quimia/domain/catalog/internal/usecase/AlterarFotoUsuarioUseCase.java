package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.account.UsuarioPerfilGateway;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Troca a foto de perfil do usuário pelo arquivo enviado à API, sem upload preset aberto no Cloudinary. Fica no
 * `catalog` porque é dele o armazenamento de imagens; a URL é gravada pelo `account`, que também decide se a foto
 * anterior pode ser apagada (outro perfil pode usar a mesma URL).
 */
@Service
public class AlterarFotoUsuarioUseCase {
    /** Tamanho de `usuario.foto_url` no banco. */
    static final int FOTO_URL_MAXIMA = 450;
    private static final String USUARIO_NAO_ENCONTRADO = "usuario_nao_encontrado";

    private final ImagemDoProduto imagens;
    private final UsuarioPerfilGateway perfil;
    private final String pasta;

    public AlterarFotoUsuarioUseCase(
            ImagemDoProduto imagens,
            UsuarioPerfilGateway perfil,
            @Value("${quimia.cloudinary.pasta-perfil:quimia/perfil}") String pasta) {
        this.imagens = imagens;
        this.perfil = perfil;
        this.pasta = pasta;
    }

    @Transactional
    public String execute(UUID usuarioId, MultipartFile arquivo) {
        byte[] imagem = imagens.ler(arquivo);
        if (imagem == null) {
            throw new CatalogException("imagem_obrigatoria", 400);
        }
        // Sem URL anterior aqui: quem apaga a antiga é o account, depois de conferir que ninguém mais a usa.
        String url = imagens.enviar(imagem, pasta, null);
        if (url.length() > FOTO_URL_MAXIMA) {
            throw new CatalogException("armazenamento_imagem_falhou", 502);
        }
        gravar(usuarioId, url);
        return url;
    }

    /** O usuário pode ter sido apagado entre o login e a gravação. */
    private void gravar(UUID usuarioId, String url) {
        try {
            perfil.atualizarFoto(usuarioId, url);
        } catch (IllegalArgumentException falha) {
            if (!USUARIO_NAO_ENCONTRADO.equals(falha.getMessage())) {
                throw falha;
            }
            throw new CatalogException(USUARIO_NAO_ENCONTRADO, 404);
        }
    }
}
