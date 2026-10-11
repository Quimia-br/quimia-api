package com.api.quimia.domain.partner.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.partner.internal.dto.PontoView;
import com.api.quimia.domain.partner.internal.usecase.ListarPontosUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pontos")
public class PontoController {
    private final ListarPontosUseCase listar;

    public PontoController(ListarPontosUseCase listar) {
        this.listar = listar;
    }

    @GetMapping
    public Pagina<PontoView> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String bairro,
            @RequestParam(required = false) String cep,
            @RequestParam(defaultValue = "false") boolean usarLocalizacaoCadastrada,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarPontosUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.usuarioId(), tipo, estado, bairro, cep, usarLocalizacaoCadastrada, pagina, tamanho);
    }
}
