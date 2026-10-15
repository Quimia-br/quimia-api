package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.internal.dto.FotoEmpresaResponse;
import com.api.quimia.domain.catalog.internal.usecase.AlterarFotoEmpresaUseCase;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Logo da empresa do portal (Perfil > trocar foto). */
@RestController
@RequestMapping("/api/v1/empresas/me/foto")
public class EmpresaFotoController {
    private final AlterarFotoEmpresaUseCase alterar;

    public EmpresaFotoController(AlterarFotoEmpresaUseCase alterar) {
        this.alterar = alterar;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FotoEmpresaResponse alterar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "imagem", required = false) MultipartFile imagem) {
        return new FotoEmpresaResponse(alterar.execute(principal.empresaId(), imagem));
    }
}
