package com.api.quimia.domain.catalog.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsForm;
import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsResponse;
import com.api.quimia.domain.catalog.internal.dto.AnaliseEmAndamentoResponse;
import com.api.quimia.domain.catalog.internal.dto.DescartarAnaliseRequest;
import com.api.quimia.domain.catalog.internal.usecase.AnalisarFdsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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
 * "substituir documento" é uma nova análise com `analiseAnterior`.
 */
@Tag(name = "Empresa: produtos")
@RestController
@RequestMapping("/api/v1/empresas/me/produtos/fds/analises")
public class EmpresaFdsController {
    private final AnalisarFdsUseCase analisar;

    public EmpresaFdsController(AnalisarFdsUseCase analisar) {
        this.analisar = analisar;
    }

    @Operation(
            summary = "Analisar FDS",
            description = "Multipart com o PDF em `fds` (até 10 MB). Guarda o PDF, lê as seções 3, 4 e 10 pelo "
                    + "extrator e devolve as pendências, sem cadastrar nada (até 60 s). Para confirmar, envie o "
                    + "`analiseId` em `analiseFds` no cadastro (`POST /produtos`) ou na alteração (`PATCH "
                    + "/produtos/{id}`): \"adicionar mesmo assim\" é confirmar sem `correcoesFds` (o campo "
                    + "faltante fica nulo); \"substituir documento\" é uma nova análise com `analiseAnterior`, que "
                    + "apaga o PDF da anterior. O `analiseId` vale 30 minutos.")
    @ApiResponse(responseCode = "200", description = "Leitura concluída, com ou sem pendências.")
    @ApiResponse(responseCode = "400", description = "Sem o arquivo `fds`: `fds_ausente`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @ApiResponse(responseCode = "413", description = "PDF acima de 10 MB: `pdf_muito_grande`.")
    @ApiResponse(responseCode = "415", description = "Arquivo que não começa com `%PDF-`: `pdf_invalido`.")
    @ApiResponse(
            responseCode = "422",
            description = "O extrator não reconheceu o PDF como FDS ou não achou texto legível: `fds_invalida`.")
    @ApiResponse(
            responseCode = "502",
            description = "Extrator ou armazenamento do PDF responderam com erro: `extrator_falhou`, "
                    + "`armazenamento_fds_falhou`.")
    @ApiResponse(
            responseCode = "503",
            description = "Extrator ou armazenamento do PDF desligados ou fora do ar: `extrator_indisponivel`, "
                    + "`armazenamento_fds_indisponivel`.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnaliseDeFdsResponse analisar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @ModelAttribute AnaliseDeFdsForm form) {
        return analisar.analisar(principal.empresaId(), form);
    }

    @Operation(
            summary = "Analisar FDS em segundo plano",
            description = "Mesmo formulário e mesmas regras de `POST /produtos/fds/analises`, mas devolve na hora um "
                    + "`id` e a leitura roda fora do pedido. O host corta respostas após cerca de 30 s e a leitura de "
                    + "uma FDS real pode passar disso: consulte `GET /produtos/fds/analises/assincronas/{id}` a cada "
                    + "2 ou 3 segundos até `concluida` ou `falhou`. Os erros de arquivo (`fds_ausente`, "
                    + "`pdf_muito_grande`, `pdf_invalido`) saem aqui, antes de criar a análise.")
    @ApiResponse(responseCode = "202", description = "Análise iniciada; o corpo traz o `id` e `processando`.")
    @ApiResponse(responseCode = "400", description = "Sem o arquivo `fds`: `fds_ausente`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @ApiResponse(responseCode = "413", description = "PDF acima de 10 MB: `pdf_muito_grande`.")
    @ApiResponse(responseCode = "415", description = "Arquivo que não começa com `%PDF-`: `pdf_invalido`.")
    @ApiResponse(
            responseCode = "429",
            description = "Análises demais em andamento neste momento: `analises_demais`. Tente de novo em instantes.")
    @PostMapping(path = "/assincronas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnaliseEmAndamentoResponse iniciar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @ModelAttribute AnaliseDeFdsForm form) {
        return analisar.iniciar(principal.empresaId(), form);
    }

    @Operation(
            summary = "Consultar análise de FDS em segundo plano",
            description = "Estado da análise iniciada em `POST /produtos/fds/analises/assincronas`: `processando`, "
                    + "`concluida` (com `analise`, o mesmo corpo da análise síncrona, inclusive o `analiseId` para o "
                    + "cadastro) ou `falhou` (com `codigo` e `detail`, o mesmo status e código que a análise síncrona "
                    + "devolveria). O `id` vale 15 minutos depois de terminar.")
    @ApiResponse(responseCode = "200", description = "Estado da análise.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(
            responseCode = "404",
            description = "Análise inexistente, vencida, perdida em um reinício ou de outra empresa: "
                    + "`analise_nao_encontrada`.")
    @GetMapping("/assincronas/{id}")
    public AnaliseEmAndamentoResponse consultar(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable String id) {
        return analisar.consultar(principal.empresaId(), id);
    }

    @Operation(
            summary = "Descartar análise de FDS",
            description = "A empresa fechou o popup sem cadastrar: apaga o PDF guardado na análise. Análise inválida "
                    + "ou de outra empresa não apaga nada. O `analiseId` vai no corpo porque é grande demais para a "
                    + "URL.")
    @ApiResponse(responseCode = "204", description = "PDF descartado, ou nada a descartar.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @PostMapping(path = "/descartar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody DescartarAnaliseRequest pedido) {
        analisar.descartar(principal.empresaId(), pedido.analiseId());
    }
}
