package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.internal.dto.FotoEmpresaResponse;
import com.api.quimia.domain.catalog.internal.usecase.AlterarFotoEmpresaUseCase;
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

/** Logo da empresa do portal (Perfil > trocar foto). */
@Tag(name = "Empresa: conta")
@RestController
@RequestMapping("/api/v1/empresas/me/foto")
public class EmpresaFotoController {
    private final AlterarFotoEmpresaUseCase alterar;

    public EmpresaFotoController(AlterarFotoEmpresaUseCase alterar) {
        this.alterar = alterar;
    }

    @Operation(
            summary = "Trocar o logo da empresa",
            description = "Envia a imagem do logo (multipart, campo `imagem`, JPEG, PNG ou WebP até 5 MB). O logo "
                    + "anterior é apagado do armazenamento depois que o novo é gravado.")
    @ApiResponse(responseCode = "200", description = "Logo trocado; devolve a URL nova.")
    @ApiResponse(responseCode = "400", description = "Campo `imagem` ausente ou vazio: `imagem_obrigatoria`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
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
    public FotoEmpresaResponse alterar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "imagem", required = false) MultipartFile imagem) {
        return new FotoEmpresaResponse(alterar.execute(principal.empresaId(), imagem));
    }
}
