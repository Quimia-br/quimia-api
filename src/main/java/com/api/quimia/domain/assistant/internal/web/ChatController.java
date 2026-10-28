package com.api.quimia.domain.assistant.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.assistant.internal.dto.EncerramentoView;
import com.api.quimia.domain.assistant.internal.dto.MensagemRequest;
import com.api.quimia.domain.assistant.internal.dto.RespostaView;
import com.api.quimia.domain.assistant.internal.usecase.ConversarComKemiUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Kemi no app e WebKemi no portal. Resposta síncrona; o histórico da conversa fica no agente. */
@RestController
public class ChatController {
    private final ConversarComKemiUseCase conversar;

    public ChatController(ConversarComKemiUseCase conversar) {
        this.conversar = conversar;
    }

    @Tag(name = "Kemi")
    @Operation(
            summary = "Perguntar ao Kemi",
            description = "Repassa a mensagem ao quimia-agent e devolve a resposta. Sem `sessaoId` a API abre uma "
                    + "conversa nova e devolve o id para continuar. Até 20 mensagens por hora por usuário.")
    @ApiResponse(responseCode = "200", description = "Resposta do Kemi.")
    @ApiResponse(responseCode = "429", description = "Limite de mensagens da hora atingido: `limite_excedido`.")
    @ApiResponse(
            responseCode = "502",
            description = "O agente respondeu com erro ou fora do contrato: `agente_falhou`.")
    @ApiResponse(
            responseCode = "503",
            description = "Agente desligado (sem `QUIMIA_AGENTE_URL`) ou fora do ar: `agente_indisponivel`.")
    @ApiResponse(responseCode = "504", description = "O agente não respondeu no prazo: `agente_timeout`.")
    @PostMapping("/api/v1/chat/mensagens")
    public RespostaView doUsuario(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody MensagemRequest mensagem) {
        return conversar.doUsuario(principal.usuarioId(), mensagem);
    }

    @Tag(name = "WebKemi")
    @Operation(
            summary = "Perguntar ao WebKemi",
            description = "O mesmo assistente para a empresa do portal. Até 20 mensagens por hora por empresa.")
    @ApiResponse(responseCode = "200", description = "Resposta do WebKemi.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @ApiResponse(responseCode = "429", description = "Limite de mensagens da hora atingido: `limite_excedido`.")
    @ApiResponse(
            responseCode = "502",
            description = "O agente respondeu com erro ou fora do contrato: `agente_falhou`.")
    @ApiResponse(
            responseCode = "503",
            description = "Agente desligado (sem `QUIMIA_AGENTE_URL`) ou fora do ar: `agente_indisponivel`.")
    @ApiResponse(responseCode = "504", description = "O agente não respondeu no prazo: `agente_timeout`.")
    @PostMapping("/api/v1/empresas/me/chat/mensagens")
    public RespostaView daEmpresa(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody MensagemRequest mensagem) {
        return conversar.daEmpresa(principal.empresaId(), mensagem);
    }

    /** "Novo chat" do app: a conversa anterior é resumida e o próximo envio, sem `sessaoId`, abre outra. */
    @Tag(name = "Kemi")
    @Operation(
            summary = "Encerrar a conversa com o Kemi",
            description = "\"Novo chat\": o agente resume a conversa e guarda o resumo; o próximo envio, sem "
                    + "`sessaoId`, abre outra. Repetir não gera outro resumo (`ja_resumida`). Não conta no limite de "
                    + "mensagens.")
    @Parameter(name = "sessaoId", description = "Conversa a encerrar: letras, dígitos, _ e -, até 64.",
            example = "b8c0e7f2-1d3a-4f5b-9c6d-2e7f8a9b0c1d")
    @ApiResponse(responseCode = "200", description = "Conversa encerrada: `resumida`, `ja_resumida` ou `sem_turnos`.")
    @ApiResponse(responseCode = "400", description = "Id de conversa fora do padrão: `sessao_invalida`.")
    @ApiResponse(
            responseCode = "502",
            description = "O agente respondeu com erro ou fora do contrato: `agente_falhou`.")
    @ApiResponse(
            responseCode = "503",
            description = "Agente desligado (sem `QUIMIA_AGENTE_URL`) ou fora do ar: `agente_indisponivel`.")
    @ApiResponse(responseCode = "504", description = "O agente não respondeu no prazo: `agente_timeout`.")
    @PostMapping("/api/v1/chat/sessoes/{sessaoId}/encerrar")
    public EncerramentoView encerrarDoUsuario(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable String sessaoId) {
        return conversar.encerrarDoUsuario(principal.usuarioId(), sessaoId);
    }

    /** "Novo chat" do portal. */
    @Tag(name = "WebKemi")
    @Operation(
            summary = "Encerrar a conversa com o WebKemi",
            description = "\"Novo chat\" do portal: o agente resume a conversa e guarda o resumo. Repetir não gera "
                    + "outro resumo (`ja_resumida`). Não conta no limite de mensagens.")
    @Parameter(name = "sessaoId", description = "Conversa a encerrar: letras, dígitos, _ e -, até 64.",
            example = "b8c0e7f2-1d3a-4f5b-9c6d-2e7f8a9b0c1d")
    @ApiResponse(responseCode = "200", description = "Conversa encerrada: `resumida`, `ja_resumida` ou `sem_turnos`.")
    @ApiResponse(responseCode = "400", description = "Id de conversa fora do padrão: `sessao_invalida`.")
    @ApiResponse(responseCode = "403", description = "Empresa desativada: `empresa_inativa`.")
    @ApiResponse(responseCode = "404", description = "Empresa do token não existe mais: `empresa_nao_encontrada`.")
    @ApiResponse(
            responseCode = "502",
            description = "O agente respondeu com erro ou fora do contrato: `agente_falhou`.")
    @ApiResponse(
            responseCode = "503",
            description = "Agente desligado (sem `QUIMIA_AGENTE_URL`) ou fora do ar: `agente_indisponivel`.")
    @ApiResponse(responseCode = "504", description = "O agente não respondeu no prazo: `agente_timeout`.")
    @PostMapping("/api/v1/empresas/me/chat/sessoes/{sessaoId}/encerrar")
    public EncerramentoView encerrarDaEmpresa(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable String sessaoId) {
        return conversar.encerrarDaEmpresa(principal.empresaId(), sessaoId);
    }
}
