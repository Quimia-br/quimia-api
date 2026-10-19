package com.api.quimia.domain.assistant.internal.web;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.assistant.internal.dto.MensagemRequest;
import com.api.quimia.domain.assistant.internal.dto.RespostaView;
import com.api.quimia.domain.assistant.internal.usecase.ConversarComKemiUseCase;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping("/api/v1/chat/mensagens")
    public RespostaView doUsuario(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody MensagemRequest mensagem) {
        return conversar.doUsuario(principal.usuarioId(), mensagem);
    }

    @PostMapping("/api/v1/empresas/me/chat/mensagens")
    public RespostaView daEmpresa(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody MensagemRequest mensagem) {
        return conversar.daEmpresa(principal.empresaId(), mensagem);
    }
}
