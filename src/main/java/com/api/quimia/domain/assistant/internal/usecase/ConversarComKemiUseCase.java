package com.api.quimia.domain.assistant.internal.usecase;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.assistant.internal.client.AgenteKemi;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiException;
import com.api.quimia.domain.assistant.internal.dto.EncerramentoView;
import com.api.quimia.domain.assistant.internal.dto.MensagemRequest;
import com.api.quimia.domain.assistant.internal.dto.RespostaView;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Repassa a mensagem ao agente e devolve a resposta. Sem transação: a chamada ao agente pode levar minutos e não
 * segura conexão do banco. Nada da conversa é gravado pela API (o histórico fica no agente).
 */
@Service
public class ConversarComKemiUseCase {
    /** Mesma regra de `MensagemRequest.sessaoId`. */
    private static final Pattern SESSAO = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final AgenteKemi agente;
    private final LimiteDeMensagens limite;
    private final AccountGateway contas;
    private final Clock relogio;

    @Autowired
    public ConversarComKemiUseCase(AgenteKemi agente, LimiteDeMensagens limite, AccountGateway contas) {
        this(agente, limite, contas, Clock.systemUTC());
    }

    ConversarComKemiUseCase(AgenteKemi agente, LimiteDeMensagens limite, AccountGateway contas, Clock relogio) {
        this.agente = agente;
        this.limite = limite;
        this.contas = contas;
        this.relogio = relogio;
    }

    /** Kemi do app. */
    public RespostaView doUsuario(UUID usuarioId, MensagemRequest mensagem) {
        return conversar("usuario:" + usuarioId, usuarioId, mensagem);
    }

    /**
     * WebKemi do portal. O agente identifica o autor por UUID; a empresa (id inteiro) vira um UUID fixo derivado do
     * id, que nunca coincide com o de um usuário (UUID v4).
     */
    public RespostaView daEmpresa(Integer empresaId, MensagemRequest mensagem) {
        return conversar("empresa:" + empresaId, autorDaEmpresa(empresaId), mensagem);
    }

    /**
     * "Novo chat" do Kemi: o agente resume a conversa anterior e guarda o resumo. Não conta no limite de mensagens, e
     * repetir a chamada não gera outro resumo.
     */
    public EncerramentoView encerrarDoUsuario(UUID usuarioId, String sessaoId) {
        return encerrar(usuarioId, sessaoId);
    }

    /** "Novo chat" do WebKemi. */
    public EncerramentoView encerrarDaEmpresa(Integer empresaId, String sessaoId) {
        return encerrar(autorDaEmpresa(empresaId), sessaoId);
    }

    private UUID autorDaEmpresa(Integer empresaId) {
        EmpresaView empresa = contas.findEmpresaById(empresaId)
                .orElseThrow(() -> new AssistantException("empresa_nao_encontrada", 404));
        if (!empresa.ativo()) {
            throw new AssistantException("empresa_inativa", 403);
        }
        return UUID.nameUUIDFromBytes(("empresa:" + empresaId).getBytes(StandardCharsets.UTF_8));
    }

    private EncerramentoView encerrar(UUID autor, String sessaoId) {
        if (sessaoId == null || !SESSAO.matcher(sessaoId).matches()) {
            throw new AssistantException("sessao_invalida", 400);
        }
        try {
            return new EncerramentoView(sessaoId, agente.encerrarSessao(autor, sessaoId));
        } catch (AgenteKemiException falha) {
            throw erroDoAgente(falha);
        }
    }

    private RespostaView conversar(String chaveDoLimite, UUID autor, MensagemRequest mensagem) {
        if (!limite.aceitar(chaveDoLimite)) {
            throw new AssistantException("limite_excedido", 429);
        }
        String sessaoId = mensagem.sessaoId() == null ? UUID.randomUUID().toString() : mensagem.sessaoId();
        try {
            String texto = agente.perguntar(autor, sessaoId, mensagem.texto().strip());
            return new RespostaView(texto, sessaoId, OffsetDateTime.now(relogio));
        } catch (AgenteKemiException falha) {
            throw erroDoAgente(falha);
        }
    }

    private static AssistantException erroDoAgente(AgenteKemiException falha) {
        return switch (falha.motivo()) {
            case INDISPONIVEL -> new AssistantException("agente_indisponivel", 503);
            case TEMPO_ESGOTADO -> new AssistantException("agente_timeout", 504);
            case FALHOU -> new AssistantException("agente_falhou", 502);
        };
    }
}
