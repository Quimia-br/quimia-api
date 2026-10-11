package com.api.quimia.domain.recommendation.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.dto.Pagina;
import com.api.quimia.domain.recommendation.internal.dto.MisturaRequest;
import com.api.quimia.domain.recommendation.internal.dto.MisturaView;
import com.api.quimia.domain.recommendation.internal.usecase.ConsultarMisturaUseCase;
import com.api.quimia.domain.recommendation.internal.usecase.ListarMisturasUseCase;
import com.api.quimia.domain.recommendation.internal.usecase.RegistrarMisturaUseCase;
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

@RestController
@RequestMapping("/api/v1/misturas")
public class MisturaController {
    private final RegistrarMisturaUseCase registrar;
    private final ListarMisturasUseCase listar;
    private final ConsultarMisturaUseCase consultar;

    public MisturaController(
            RegistrarMisturaUseCase registrar, ListarMisturasUseCase listar, ConsultarMisturaUseCase consultar) {
        this.registrar = registrar;
        this.listar = listar;
        this.consultar = consultar;
    }

    @PostMapping
    public ResponseEntity<MisturaView> registrar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody MisturaRequest request) {
        MisturaView mistura = registrar.execute(principal.usuarioId(), request.produtoIdA(), request.produtoIdB());
        return ResponseEntity.created(URI.create("/api/v1/misturas/" + mistura.id())).body(mistura);
    }

    @GetMapping
    public Pagina<MisturaView> listar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "produtoId", required = false) Integer produtoId,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "" + ListarMisturasUseCase.TAMANHO_PADRAO) int tamanho) {
        return listar.execute(principal.usuarioId(), produtoId, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public MisturaView consultar(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Integer id) {
        return consultar.execute(principal.usuarioId(), id);
    }
}
