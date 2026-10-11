package com.api.quimia.domain.safety.internal.persistence;

import com.api.quimia.domain.safety.CompatibilidadeGateway;
import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import com.api.quimia.domain.safety.internal.model.ComposicaoDoProduto;
import com.api.quimia.domain.safety.internal.model.IncompatibilidadeRegra;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * `@Component` e não `@Repository`: a tradução de exceções do `@Repository` converteria a
 * `IllegalArgumentException("produtos_iguais")` do contrato em `InvalidDataAccessApiUsageException`.
 *
 * <p>Reproduz `fn_match_produtos` (que o H2 do build não tem) e corrige o risco R4: composição
 * parcialmente resolvida e sem regra casando vira `nao_avaliado`, não `compativel`.
 */
@Component
@Transactional(readOnly = true)
public class JpaCompatibilidadeGateway implements CompatibilidadeGateway {
    static final String COMPATIVEL = "compativel";
    static final String INCOMPATIVEL = "incompativel";
    static final String NAO_AVALIADO = "nao_avaliado";
    static final String SEM_COMPOSICAO = "Produto sem composição resolvida";
    static final String COMPOSICAO_INCOMPLETA = "Composição incompleta: há compostos não identificados";

    private static final Map<String, Integer> PESO_SEVERIDADE =
            Map.of("critica", 4, "alta", 3, "media", 2, "baixa", 1);
    private static final int PESO_ALTA = PESO_SEVERIDADE.get("alta");
    private static final int MINIMO_PARA_UM_PAR = 2;

    @PersistenceContext
    private EntityManager em;

    @Override
    public AvaliacaoParView avaliarPar(Integer produtoIdA, Integer produtoIdB) {
        if (produtoIdA == null || produtoIdB == null || produtoIdA.equals(produtoIdB)) {
            throw new IllegalArgumentException("produtos_iguais");
        }
        int menor = Math.min(produtoIdA, produtoIdB);
        int maior = Math.max(produtoIdA, produtoIdB);
        return avaliar(menor, maior, carregar(List.of(menor, maior)));
    }

    @Override
    public AvaliacaoConjuntoView avaliarConjunto(Collection<Integer> produtoIds) {
        List<Integer> ids = produtoIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.size() < MINIMO_PARA_UM_PAR) {
            return new AvaliacaoConjuntoView(NAO_AVALIADO, ids.size(), 0, 0, 0, null, List.of());
        }
        Carga carga = carregar(ids);
        List<AvaliacaoParView> incompativeis = new ArrayList<>();
        int avaliados = 0;
        int naoAvaliados = 0;
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                AvaliacaoParView par = avaliar(ids.get(i), ids.get(j), carga);
                if (NAO_AVALIADO.equals(par.resultado())) {
                    naoAvaliados++;
                } else {
                    avaliados++;
                    if (INCOMPATIVEL.equals(par.resultado())) {
                        incompativeis.add(par);
                    }
                }
            }
        }
        incompativeis.sort(Comparator
                .comparing((AvaliacaoParView par) -> PESO_SEVERIDADE.getOrDefault(par.severidade(), 0))
                .reversed()
                .thenComparing(AvaliacaoParView::regraId));
        String severidadeMaxima = incompativeis.isEmpty() ? null : incompativeis.get(0).severidade();
        return new AvaliacaoConjuntoView(
                statusDoConjunto(severidadeMaxima, naoAvaliados),
                ids.size(),
                avaliados,
                incompativeis.size(),
                naoAvaliados,
                severidadeMaxima,
                List.copyOf(incompativeis));
    }

    private static String statusDoConjunto(String severidadeMaxima, int naoAvaliados) {
        if (severidadeMaxima != null) {
            return PESO_SEVERIDADE.get(severidadeMaxima) >= PESO_ALTA ? "critico" : "atencao";
        }
        return naoAvaliados > 0 ? NAO_AVALIADO : "seguro";
    }

    private AvaliacaoParView avaliar(int a, int b, Carga carga) {
        ComposicaoDoProduto ca = carga.composicoes().getOrDefault(a, ComposicaoDoProduto.VAZIA);
        ComposicaoDoProduto cb = carga.composicoes().getOrDefault(b, ComposicaoDoProduto.VAZIA);
        if (ca.semSubstanciaResolvida() || cb.semSubstanciaResolvida()) {
            return new AvaliacaoParView(a, b, NAO_AVALIADO, null, SEM_COMPOSICAO, null);
        }
        IncompatibilidadeRegra pior = null;
        for (IncompatibilidadeRegra regra : carga.regras()) {
            if ((casa(regra, ca, cb) || casa(regra, cb, ca)) && maisGrave(regra, pior)) {
                pior = regra;
            }
        }
        if (pior != null) {
            return new AvaliacaoParView(a, b, INCOMPATIVEL, pior.getSeveridade(), pior.getDescricaoRisco(), pior.getId());
        }
        if (ca.temCompostoNaoResolvido() || cb.temCompostoNaoResolvido()) {
            return new AvaliacaoParView(a, b, NAO_AVALIADO, null, COMPOSICAO_INCOMPLETA, null);
        }
        return new AvaliacaoParView(a, b, COMPATIVEL, null, null, null);
    }

    /** O lado A da regra casa com `x` e o lado B com `y`. */
    private static boolean casa(IncompatibilidadeRegra regra, ComposicaoDoProduto x, ComposicaoDoProduto y) {
        return ladoCasa(regra.getSubstanciaAId(), regra.getClasseAId(), x)
                && ladoCasa(regra.getSubstanciaBId(), regra.getClasseBId(), y);
    }

    private static boolean ladoCasa(Integer substancia, Integer classe, ComposicaoDoProduto composicao) {
        if (substancia != null) {
            return composicao.substancias().contains(substancia);
        }
        return classe != null && composicao.classes().contains(classe);
    }

    /** `candidata` é mais grave que `atual`; empate pelo menor id. */
    private static boolean maisGrave(IncompatibilidadeRegra candidata, IncompatibilidadeRegra atual) {
        if (atual == null) {
            return true;
        }
        int pesoCandidata = PESO_SEVERIDADE.getOrDefault(candidata.getSeveridade(), 0);
        int pesoAtual = PESO_SEVERIDADE.getOrDefault(atual.getSeveridade(), 0);
        if (pesoCandidata != pesoAtual) {
            return pesoCandidata > pesoAtual;
        }
        return candidata.getId() < atual.getId();
    }

    private Carga carregar(Collection<Integer> produtoIds) {
        Map<Integer, Set<Integer>> substanciasPorProduto = new HashMap<>();
        Set<Integer> comNaoResolvido = new HashSet<>();
        List<Object[]> compostos = em.createQuery(
                        """
                        select f.produtoId, c.substanciaId from Fds f, FdsComposto c
                        where c.fdsId = f.id and f.ativo = true and f.produtoId in :produtos
                        """,
                        Object[].class)
                .setParameter("produtos", produtoIds)
                .getResultList();
        for (Object[] linha : compostos) {
            Integer produto = (Integer) linha[0];
            Integer substancia = (Integer) linha[1];
            if (substancia == null) {
                comNaoResolvido.add(produto);
            } else {
                substanciasPorProduto.computeIfAbsent(produto, id -> new HashSet<>()).add(substancia);
            }
        }
        Set<Integer> todasSubstancias = new HashSet<>();
        substanciasPorProduto.values().forEach(todasSubstancias::addAll);
        Map<Integer, Set<Integer>> classesPorSubstancia = classesPorSubstancia(todasSubstancias);

        Map<Integer, ComposicaoDoProduto> composicoes = new HashMap<>();
        for (Integer produto : produtoIds) {
            Set<Integer> substancias = substanciasPorProduto.getOrDefault(produto, Set.of());
            Set<Integer> classes = new HashSet<>();
            substancias.forEach(substancia -> classes.addAll(classesPorSubstancia.getOrDefault(substancia, Set.of())));
            composicoes.put(produto, new ComposicaoDoProduto(substancias, classes, comNaoResolvido.contains(produto)));
        }
        List<IncompatibilidadeRegra> regras = em.createQuery(
                        "select r from IncompatibilidadeRegra r where r.ativo = true order by r.id",
                        IncompatibilidadeRegra.class)
                .getResultList();
        return new Carga(composicoes, regras);
    }

    private Map<Integer, Set<Integer>> classesPorSubstancia(Collection<Integer> substanciaIds) {
        Map<Integer, Set<Integer>> classes = new HashMap<>();
        if (substanciaIds.isEmpty()) {
            return classes;
        }
        List<Object[]> linhas = em.createQuery(
                        "select sc.substanciaId, sc.classeQuimicaId from SubstanciaClasseQuimica sc"
                                + " where sc.substanciaId in :substancias",
                        Object[].class)
                .setParameter("substancias", substanciaIds)
                .getResultList();
        for (Object[] linha : linhas) {
            classes.computeIfAbsent((Integer) linha[0], id -> new HashSet<>()).add((Integer) linha[1]);
        }
        return classes;
    }

    private record Carga(Map<Integer, ComposicaoDoProduto> composicoes, List<IncompatibilidadeRegra> regras) {}
}
