package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.account.EmpresaPerfilGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Troca o logo da empresa. Fica no `catalog` porque é dele o armazenamento de imagens; a URL é
 * gravada pelo `account` (evita o ciclo account → catalog → account).
 *
 * O logo vai para a pasta da própria empresa (`empresas/{id}`) e só o que está nela é apagado na troca: a URL
 * atual pode ter sido gravada pelo cliente em `PATCH /empresas/me` e apontar para a imagem de outra empresa.
 */
@Service
public class AlterarFotoEmpresaUseCase {
    /** Tamanho de `empresa.foto_url` no banco. */
    static final int FOTO_URL_MAXIMA = 200;
    private static final String PASTA_EMPRESAS = "empresas/";
    private static final String EMPRESA_NAO_ENCONTRADA = "empresa_nao_encontrada";

    private final EmpresaDoPortal portal;
    private final ImagemDoProduto imagens;
    private final EmpresaPerfilGateway perfil;

    public AlterarFotoEmpresaUseCase(EmpresaDoPortal portal, ImagemDoProduto imagens, EmpresaPerfilGateway perfil) {
        this.portal = portal;
        this.imagens = imagens;
        this.perfil = perfil;
    }

    @Transactional
    public String execute(Integer empresaId, MultipartFile arquivo) {
        portal.exigirAtiva(empresaId);
        byte[] imagem = imagens.ler(arquivo);
        if (imagem == null) {
            throw new CatalogException("imagem_obrigatoria", 400);
        }
        String urlAnterior = perfil.fotoAtual(empresaId).orElse(null);
        String url = imagens.enviar(imagem, PASTA_EMPRESAS + empresaId, urlAnterior);
        if (url.length() > FOTO_URL_MAXIMA) {
            throw new CatalogException("armazenamento_imagem_falhou", 502);
        }
        gravar(empresaId, url);
        return url;
    }

    /** A empresa pode ter sido apagada entre a checagem e a gravação. */
    private void gravar(Integer empresaId, String url) {
        try {
            perfil.atualizarFoto(empresaId, url);
        } catch (IllegalArgumentException falha) {
            if (!EMPRESA_NAO_ENCONTRADA.equals(falha.getMessage())) {
                throw falha;
            }
            throw new CatalogException(EMPRESA_NAO_ENCONTRADA, 404);
        }
    }
}
