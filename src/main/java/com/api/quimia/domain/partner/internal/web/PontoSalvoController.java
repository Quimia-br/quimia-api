package com.api.quimia.domain.partner.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.partner.internal.dto.PontoSalvoView;
import com.api.quimia.domain.partner.internal.usecase.ConsultarUltimoPontoSalvoUseCase;
import com.api.quimia.domain.partner.internal.usecase.ListarPontosSalvosUseCase;
import com.api.quimia.domain.partner.internal.usecase.RemoverPontoSalvoUseCase;
import com.api.quimia.domain.partner.internal.usecase.SalvarPontoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pontos")
@RestController
@RequestMapping("/api/v1/pontos/salvos")
public class PontoSalvoController {
    private final SalvarPontoUseCase salvar;
    private final ListarPontosSalvosUseCase listar;
    private final ConsultarUltimoPontoSalvoUseCase ultimo;
    private final RemoverPontoSalvoUseCase remover;

    public PontoSalvoController(
            SalvarPontoUseCase salvar,
            ListarPontosSalvosUseCase listar,
            ConsultarUltimoPontoSalvoUseCase ultimo,
            RemoverPontoSalvoUseCase remover) {
        this.salvar = salvar;
        this.listar = listar;
        this.ultimo = ultimo;
        this.remover = remover;
    }

    @Operation(
            summary = "Salvar ponto",
            description = "Salva um ponto parceiro ativo para o usuário. Salvar de novo não é erro e mantém a data "
                    + "original.")
    @Parameter(name = "pontoId", description = "Id do ponto parceiro.", example = "8")
    @ApiResponse(responseCode = "200", description = "Ponto salvo.")
    @ApiResponse(responseCode = "404", description = "Ponto inexistente ou desativado: `ponto_nao_encontrado`.")
    @PutMapping("/{pontoId}")
    public PontoSalvoView salvar(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer pontoId) {
        return salvar.execute(principal.usuarioId(), pontoId);
    }

    @Operation(
            summary = "Pontos salvos",
            description = "Pontos salvos pelo usuário, do mais recente ao mais antigo. Pontos desativados depois de "
                    + "salvos não aparecem.")
    @Parameter(name = "tipo", description = "compra ou descarte; ausente traz os dois.", example = "descarte")
    @Parameter(name = "page", description = "Página, a partir de 0.", example = "0")
    @Parameter(name = "size", description = "Itens por página, de 1 a 50.", example = "20")
    @ApiResponse(responseCode = "200", description = "Página de pontos salvos.")
    @ApiResponse(
            responseCode = "400",
            description = "Paginação fora das regras (página negativa, tamanho fora de 1 a 50 ou página × tamanho "
                    + "acima do maior int): `paginacao_invalida`. Tipo fora da lista: `tipo_invalido`.")
    @GetMapping
    public Pagina<PontoSalvoView> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String tipo,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarPontosSalvosUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.usuarioId(), tipo, pagina, tamanho);
    }

    @Operation(
            summary = "Último ponto salvo",
            description = "O ponto salvo mais recente; com `tipo=descarte`, o último descarte da Home. Removido o "
                    + "último, vale o anterior.")
    @Parameter(name = "tipo", description = "compra ou descarte; ausente considera os dois.", example = "descarte")
    @ApiResponse(responseCode = "200", description = "Último ponto salvo.")
    @ApiResponse(responseCode = "400", description = "Tipo fora da lista: `tipo_invalido`.")
    @ApiResponse(
            responseCode = "404",
            description = "Nenhum ponto salvo (do tipo pedido): `ponto_salvo_nao_encontrado`.")
    @GetMapping("/ultimo")
    public PontoSalvoView ultimo(
            @AuthenticationPrincipal AccountPrincipal principal, @RequestParam(required = false) String tipo) {
        return ultimo.execute(principal.usuarioId(), tipo);
    }

    @Operation(
            summary = "Remover ponto salvo",
            description = "Tira o ponto dos salvos do usuário. Remover um ponto que não estava salvo não é erro.")
    @Parameter(name = "pontoId", description = "Id do ponto parceiro.", example = "8")
    @ApiResponse(responseCode = "204", description = "Removido dos salvos.")
    @DeleteMapping("/{pontoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer pontoId) {
        remover.execute(principal.usuarioId(), pontoId);
    }
}
