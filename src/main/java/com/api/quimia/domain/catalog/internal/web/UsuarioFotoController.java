package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.internal.dto.FotoUsuarioResponse;
import com.api.quimia.domain.catalog.internal.usecase.AlterarFotoUsuarioUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Foto de perfil do usuário do app (Perfil > trocar foto). */
@Tag(name = "Usuário: conta")
@RestController
@RequestMapping("/api/v1/usuarios/me/foto")
public class UsuarioFotoController {
    private final AlterarFotoUsuarioUseCase alterar;

    public UsuarioFotoController(AlterarFotoUsuarioUseCase alterar) {
        this.alterar = alterar;
    }

    @Operation(
            summary = "Trocar a foto de perfil",
            description = "Envia a foto (multipart, campo `imagem`, JPEG, PNG ou WebP até 5 MB); a API grava no "
                    + "armazenamento e salva a URL no perfil. A foto anterior é apagada depois que a nova é gravada, "
                    + "salvo se outro perfil ainda usa a mesma URL.")
    @ApiResponse(responseCode = "200", description = "Foto trocada; devolve a URL nova.")
    @ApiResponse(responseCode = "400", description = "Campo `imagem` ausente ou vazio: `imagem_obrigatoria`.")
    @ApiResponse(responseCode = "404", description = "Usuário do token não existe mais: `usuario_nao_encontrado`.")
    @ApiResponse(responseCode = "413", description = "Imagem acima de 5 MB: `imagem_muito_grande`.")
    @ApiResponse(
            responseCode = "415",
            description = "Arquivo que não é JPEG, PNG ou WebP pelo conteúdo: `imagem_invalida`.")
    @ApiResponse(
            responseCode = "502",
            description = "O armazenamento de imagens recusou o envio: `armazenamento_imagem_falhou`.")
    @ApiResponse(
            responseCode = "503",
            description = "Armazenamento de imagens desligado (sem `CLOUDINARY_*`) ou fora do ar: "
                    + "`armazenamento_imagem_indisponivel`.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FotoUsuarioResponse alterar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "imagem", required = false) MultipartFile imagem) {
        return new FotoUsuarioResponse(alterar.execute(principal.usuarioId(), imagem));
    }
}
