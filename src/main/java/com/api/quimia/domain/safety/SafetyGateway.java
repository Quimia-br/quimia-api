package com.api.quimia.domain.safety;

import com.api.quimia.domain.safety.dto.ComposicaoView;
import com.api.quimia.domain.safety.dto.ResumoSimplificadoView;
import com.api.quimia.domain.safety.dto.SubstanciaNaEstanteView;
import com.api.quimia.domain.safety.dto.SubstanciaView;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Contrato público da segurança química para outros módulos. Quem chama valida o acesso ao produto. */
public interface SafetyGateway {
    /** Resumo da FDS ativa mais recente; com campos nulos quando o produto não tem FDS ou texto. */
    ResumoSimplificadoView resumoSimplificado(Integer produtoId);

    /** Substâncias da FDS ativa mais recente do produto, ordenadas por nome. */
    List<ComposicaoView> composicao(Integer produtoId);

    /** Substâncias presentes nos produtos informados, cada uma com os produtos em que aparece. */
    List<SubstanciaNaEstanteView> substanciasDosProdutos(Collection<Integer> produtoIds);

    Optional<SubstanciaView> substancia(Integer substanciaId);
}
