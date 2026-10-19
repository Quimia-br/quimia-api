package com.api.quimia.domain.safety.internal.persistence;

import com.api.quimia.domain.catalog.event.FdsRecebidaEvent;
import com.api.quimia.domain.safety.internal.model.NovaFds;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a FDS que o portal enviou, na mesma transação do cadastro ou da alteração do produto: se falhar, o produto
 * também não é gravado. As FDS anteriores do produto deixam de ser ativas; a lida pela API é a ativa mais recente.
 */
@Component
public class RegistroDeFdsRecebida {
    /** `fds.data_atualizacao` é uma data, sem fuso: a do dia no Brasil. */
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @PersistenceContext
    private EntityManager em;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(FdsRecebidaEvent evento) {
        em.createNativeQuery("UPDATE fds SET ativo = FALSE WHERE id_produto = :produto AND ativo = TRUE")
                .setParameter("produto", evento.produtoId())
                .executeUpdate();
        em.persist(new NovaFds(evento.produtoId(), evento.rawJson(), evento.fonteUrl(), LocalDate.now(FUSO)));
        em.flush();
    }
}
