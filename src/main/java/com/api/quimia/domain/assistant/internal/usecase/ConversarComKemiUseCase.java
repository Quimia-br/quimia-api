package com.api.quimia.domain.assistant.internal.usecase;

import com.api.quimia.domain.account.AccountGateway;
import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.assistant.internal.client.AgenteKemi;
import com.api.quimia.domain.assistant.internal.client.AgenteKemiException;
import com.api.quimia.domain.assistant.internal.dto.MensagemRequest;
import com.api.quimia.domain.assistant.internal.dto.RespostaView;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Repassa a mensagem ao agente e devolve a resposta. Sem transação: a chamada ao agente pode levar minutos e não
 * segura conexão do banco. Nada da conversa é gravado pela API (o histórico fica no agente).
 */
@Service
public class ConversarComKemiUseCase {
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
        EmpresaView empresa = contas.findEmpresaById(empresaId)
                .orElseThrow(() -> new AssistantException("empresa_nao_encontrada", 404));
        if (!empresa.ativo()) {
            throw new AssistantException("empresa_inativa", 403);
        }
        UUID autor = UUID.nameUUIDFromBytes(("empresa:" + empresaId).getBytes(StandardCharsets.UTF_8));
        return conversar("empresa:" + empresaId, autor, mensagem);
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
            throw switch (falha.motivo()) {
                case INDISPONIVEL -> new AssistantException("agente_indisponivel", 503);
                case TEMPO_ESGOTADO -> new AssistantException("agente_timeout", 504);
                case FALHOU -> new AssistantException("agente_falhou", 502);
            };
        }
    }
}
