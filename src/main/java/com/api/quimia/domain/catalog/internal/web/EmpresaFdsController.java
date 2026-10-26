package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsForm;
import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsResponse;
import com.api.quimia.domain.catalog.internal.dto.DescartarAnaliseRequest;
import com.api.quimia.domain.catalog.internal.usecase.AnalisarFdsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Revisão da FDS antes do cadastro ("Estamos analisando sua FDS" e "Pendências identificadas" do portal).
 *
 * A análise guarda o PDF, lê as seções 3, 4 e 10 pelo extrator (até 60 s) e devolve as pendências, sem cadastrar nada.
 * Para confirmar, o portal envia o `analiseId` em `analiseFds` no cadastro (`POST /produtos`) ou na alteração
 * (`PATCH /produtos/{id}`): "adicionar mesmo assim" é confirmar sem `correcoesFds` (o campo faltante fica nulo) e
 * "substituir documento" é uma nova análise com `analiseAnterior`. Erros: o do extrator e o do armazenamento são os do
 * cadastro com FDS (`fds_invalida`, `pdf_muito_grande`, `pdf_invalido`, `extrator_*`, `armazenamento_fds_*`);
 * `fds_ausente` quando falta o arquivo.
 */
@RestController
@RequestMapping("/api/v1/empresas/me/produtos/fds/analises")
public class EmpresaFdsController {
    private final AnalisarFdsUseCase analisar;

    public EmpresaFdsController(AnalisarFdsUseCase analisar) {
        this.analisar = analisar;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnaliseDeFdsResponse analisar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @ModelAttribute AnaliseDeFdsForm form) {
        return analisar.analisar(principal.empresaId(), form);
    }

    /**
     * A empresa fechou o popup sem cadastrar: apaga o PDF guardado na análise. Análise inválida ou de outra empresa não
     * apaga nada. O `analiseId` vai no corpo porque é grande demais para a URL.
     */
    @PostMapping(path = "/descartar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody DescartarAnaliseRequest pedido) {
        analisar.descartar(principal.empresaId(), pedido.analiseId());
    }
}
