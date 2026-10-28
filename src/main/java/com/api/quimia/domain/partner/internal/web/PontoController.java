package com.api.quimia.domain.partner.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.partner.internal.dto.PontoView;
import com.api.quimia.domain.partner.internal.usecase.ListarPontosUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pontos")
@RestController
@RequestMapping("/api/v1/pontos")
public class PontoController {
    private final ListarPontosUseCase listar;

    public PontoController(ListarPontosUseCase listar) {
        this.listar = listar;
    }

    @Operation(
            summary = "Pontos de compra e de descarte",
            description = "Pontos parceiros ativos do `tipo` pedido. Sem distância (o banco não tem coordenadas): "
                    + "filtra por `estado`, `bairro` e `cep`, ou pelo endereço do usuário com "
                    + "`usarLocalizacaoCadastrada=true`.")
    @Parameter(name = "tipo", description = "compra ou descarte (obrigatório).", example = "descarte")
    @Parameter(name = "estado", description = "UF com 2 letras.", example = "SP")
    @Parameter(name = "bairro", description = "Bairro, sem acento nem caixa.", example = "Pinheiros")
    @Parameter(name = "cep", description = "CEP com 8 dígitos.", example = "05422000")
    @Parameter(
            name = "usarLocalizacaoCadastrada",
            description = "Usa o estado e o bairro do endereço do usuário.",
            example = "true")
    @Parameter(name = "page", description = "Página, a partir de 0.", example = "0")
    @Parameter(name = "size", description = "Itens por página, de 1 a 50.", example = "20")
    @ApiResponse(responseCode = "200", description = "Página de pontos.")
    @ApiResponse(
            responseCode = "400",
            description = "Paginação fora das regras (página negativa, tamanho fora de 1 a 50 ou página × tamanho "
                    + "acima do maior int): `paginacao_invalida`. Tipo ausente ou fora da lista: "
                    + "`tipo_invalido`.")
    @ApiResponse(
            responseCode = "422",
            description = "UF ou CEP inválidos, ou endereço do usuário ausente: `estado_invalido`, "
                    + "`cep_invalido`, `localizacao_necessaria`.")
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
