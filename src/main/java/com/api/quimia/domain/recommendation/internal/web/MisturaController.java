package com.api.quimia.domain.recommendation.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.recommendation.dto.ResumoMisturasView;
import com.api.quimia.domain.recommendation.internal.dto.MisturaRequest;
import com.api.quimia.domain.recommendation.internal.dto.MisturaView;
import com.api.quimia.domain.recommendation.internal.usecase.ConsultarMisturaUseCase;
import com.api.quimia.domain.recommendation.internal.usecase.ListarMisturasUseCase;
import com.api.quimia.domain.recommendation.internal.usecase.RegistrarMisturaUseCase;
import com.api.quimia.domain.recommendation.internal.usecase.ResumirMisturasUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Misturas")
@RestController
@RequestMapping("/api/v1/misturas")
public class MisturaController {
    private final RegistrarMisturaUseCase registrar;
    private final ListarMisturasUseCase listar;
    private final ConsultarMisturaUseCase consultar;
    private final ResumirMisturasUseCase resumir;

    public MisturaController(
            RegistrarMisturaUseCase registrar,
            ListarMisturasUseCase listar,
            ConsultarMisturaUseCase consultar,
            ResumirMisturasUseCase resumir) {
        this.registrar = registrar;
        this.listar = listar;
        this.consultar = consultar;
        this.resumir = resumir;
    }

    @Operation(
            summary = "Misturar dois produtos",
            description = "Avalia o par pelas regras de incompatibilidade e grava a consulta no histórico (cada "
                    + "chamada é uma consulta nova; não há análise sem gravar). `incompativel` traz a severidade e a "
                    + "descrição da regra mais grave; `nao_avaliado` traz o motivo (produto sem composição ou com "
                    + "composto não identificado). Só pares de produtos da estante: substâncias soltas e "
                    + "combinações de produto com substância não são aceitas. `dosagem` é sempre nula nesta versão.")
    @ApiResponse(
            responseCode = "201",
            description = "Consulta registrada.",
            headers = @Header(name = "Location", description = "URL da mistura (`/api/v1/misturas/{id}`)."))
    @ApiResponse(
            responseCode = "404",
            description = "Algum dos produtos fora da estante do usuário: `produto_nao_esta_na_estante`.")
    @ApiResponse(responseCode = "422", description = "Os dois ids iguais: `produtos_iguais`.")
    @PostMapping
    public ResponseEntity<MisturaView> registrar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody MisturaRequest request) {
        MisturaView mistura = registrar.execute(principal.usuarioId(), request.produtoIdA(), request.produtoIdB());
        return ResponseEntity.created(URI.create("/api/v1/misturas/" + mistura.id())).body(mistura);
    }

    @Operation(
            summary = "Histórico de misturas",
            description = "Misturas do usuário, da mais recente para a mais antiga. `produtoId` mostra só as que "
                    + "envolvem o produto (\"Ver misturas\").")
    @Parameter(
            name = "produtoId",
            description = "Só misturas com este produto, em qualquer lado do par.",
            example = "42")
    @Parameter(name = "page", description = "Página, a partir de 0.", example = "0")
    @Parameter(name = "size", description = "Itens por página, de 1 a 50.", example = "20")
    @ApiResponse(responseCode = "200", description = "Página de misturas.")
    @ApiResponse(
            responseCode = "400",
            description = "Paginação fora das regras (página negativa, tamanho fora de 1 a 50 ou página × tamanho "
                    + "acima do maior int): `paginacao_invalida`.")
    @GetMapping
    public Pagina<MisturaView> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "produtoId", required = false) Integer produtoId,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarMisturasUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.usuarioId(), produtoId, pagina, tamanho);
    }

    @Operation(
            summary = "Resumo do histórico de misturas",
            description = "Totais das misturas do usuário por resultado, para a Home. Atualiza a cada "
                    + "`POST /misturas`.")
    @ApiResponse(responseCode = "200", description = "Contagem por resultado; zeros sem histórico.")
    @GetMapping("/resumo")
    public ResumoMisturasView resumo(@AuthenticationPrincipal AccountPrincipal principal) {
        return resumir.execute(principal.usuarioId());
    }

    @Operation(summary = "Detalhe da mistura", description = "Uma consulta do histórico.")
    @Parameter(name = "id", description = "Id da mistura.", example = "15")
    @ApiResponse(responseCode = "200", description = "Mistura.")
    @ApiResponse(
            responseCode = "404",
            description = "Mistura inexistente ou de outro usuário: `mistura_nao_encontrada`.")
    @GetMapping("/{id}")
    public MisturaView consultar(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer id) {
        return consultar.execute(principal.usuarioId(), id);
    }
}
