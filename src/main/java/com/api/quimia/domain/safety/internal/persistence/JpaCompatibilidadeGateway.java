package com.api.quimia.domain.safety.internal.persistence;

import com.api.quimia.domain.safety.CompatibilidadeGateway;
import com.api.quimia.domain.safety.dto.AvaliacaoConjuntoView;
import com.api.quimia.domain.safety.dto.AvaliacaoItensView;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import com.api.quimia.domain.safety.dto.ItemParaAvaliar;
import com.api.quimia.domain.safety.internal.model.ComposicaoDoProduto;
import com.api.quimia.domain.safety.internal.model.IncompatibilidadeDaFds;
import com.api.quimia.domain.safety.internal.model.IncompatibilidadeRegra;
import com.api.quimia.domain.safety.internal.model.MotorDeCompatibilidade;
import com.api.quimia.domain.safety.internal.model.RegraDeIncompatibilidade;
import com.api.quimia.domain.safety.internal.model.Severidade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega as composições e as regras que podem casar e entrega a decisão ao {@link MotorDeCompatibilidade}.
 *
 * <p>`@Component` e não `@Repository`: a tradução de exceções do `@Repository` converteria a
 * `IllegalArgumentException("produtos_iguais")` do contrato em `InvalidDataAccessApiUsageException`.
 */
@Component
@Transactional(readOnly = true)
public class JpaCompatibilidadeGateway implements CompatibilidadeGateway {
    @PersistenceContext
    private EntityManager em;

    @Override
    public AvaliacaoParView avaliarPar(Integer produtoIdA, Integer produtoIdB) {
        List<Integer> ids = Stream.of(produtoIdA, produtoIdB).filter(Objects::nonNull).distinct().toList();
        return motor(ids, List.of()).par(produtoIdA, produtoIdB);
    }

    @Override
    public AvaliacaoItensView avaliarItens(ItemParaAvaliar a, ItemParaAvaliar b) {
        List<ItemParaAvaliar> itens = Stream.of(a, b).filter(Objects::nonNull).toList();
        List<Integer> produtos =
                itens.stream().map(ItemParaAvaliar::produtoId).filter(Objects::nonNull).distinct().toList();
        List<Integer> substancias =
                itens.stream().map(ItemParaAvaliar::substanciaId).filter(Objects::nonNull).distinct().toList();
        return motor(produtos, substancias).avaliar(a, b);
    }

    @Override
    public AvaliacaoConjuntoView avaliarConjunto(Collection<Integer> produtoIds) {
        List<Integer> ids = produtoIds.stream().filter(Objects::nonNull).distinct().toList();
        return motor(ids, List.of()).conjunto(ids);
    }

    /** `substanciaIds`: substâncias soltas, sem produto, que também entram na avaliação. */
    private MotorDeCompatibilidade motor(Collection<Integer> produtoIds, Collection<Integer> substanciaIds) {
        if (produtoIds.isEmpty() && substanciaIds.isEmpty()) {
            return new MotorDeCompatibilidade(Map.of(), List.of());
        }
        Map<Integer, Set<Integer>> substanciasPorProduto = new HashMap<>();
        Set<Integer> comNaoResolvido = new HashSet<>();
        for (Object[] linha : compostosDosProdutos(produtoIds)) {
            Integer produto = (Integer) linha[0];
            Integer substancia = (Integer) linha[1];
            if (substancia == null) {
                comNaoResolvido.add(produto);
            } else {
                substanciasPorProduto.computeIfAbsent(produto, id -> new HashSet<>()).add(substancia);
            }
        }
        Set<Integer> substancias = new HashSet<>(substanciaIds);
        substanciasPorProduto.values().forEach(substancias::addAll);
        if (substancias.isEmpty()) {
            // Sem substância identificada nenhum par é avaliável: classes e regras não são consultadas.
            return new MotorDeCompatibilidade(Map.of(), List.of());
        }
        Map<Integer, Set<Integer>> classesPorSubstancia = classesPorSubstancia(substancias);

        Map<Integer, ComposicaoDoProduto> composicoes = new HashMap<>();
        Set<Integer> classes = new HashSet<>();
        for (Integer produto : produtoIds) {
            Set<Integer> doProduto = substanciasPorProduto.getOrDefault(produto, Set.of());
            Set<Integer> classesDoProduto = new HashSet<>();
            doProduto.forEach(substancia -> classesDoProduto.addAll(classesPorSubstancia.getOrDefault(substancia, Set.of())));
            classes.addAll(classesDoProduto);
            composicoes.put(produto, new ComposicaoDoProduto(doProduto, classesDoProduto, comNaoResolvido.contains(produto)));
        }
        Map<Integer, ComposicaoDoProduto> avulsas = new HashMap<>();
        for (Integer substancia : substanciaIds) {
            Set<Integer> classesDaSubstancia = classesPorSubstancia.getOrDefault(substancia, Set.of());
            classes.addAll(classesDaSubstancia);
            avulsas.put(substancia, new ComposicaoDoProduto(Set.of(substancia), classesDaSubstancia, false));
        }
        return new MotorDeCompatibilidade(
                composicoes, avulsas, regrasQuePodemCasar(substancias, classes), declaradasPelasFds(produtoIds));
    }

    private List<Object[]> compostosDosProdutos(Collection<Integer> produtoIds) {
        if (produtoIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        """
                        select f.produtoId, c.substanciaId from Fds f, FdsComposto c
                        where c.fdsId = f.id and f.ativo = true and f.produtoId in :produtos
                        """,
                        Object[].class)
                .setParameter("produtos", produtoIds)
                .getResultList();
    }

    /**
     * Incompatibilidades das seções 10 das FDS ativas, só as que o banco resolveu para substância ou classe (o resto é
     * texto, como "temperaturas altas", e não entra no motor).
     */
    private List<IncompatibilidadeDaFds> declaradasPelasFds(Collection<Integer> produtoIds) {
        if (produtoIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        """
                        select f.produtoId, i.substanciaId, i.classeQuimicaId, i.severidade, i.descricaoRisco
                        from Fds f, FdsIncompatibilidade i
                        where i.fdsId = f.id and f.ativo = true and f.produtoId in :produtos
                          and (i.substanciaId is not null or i.classeQuimicaId is not null)
                        """,
                        Object[].class)
                .setParameter("produtos", produtoIds)
                .getResultList()
                .stream()
                .map(linha -> new IncompatibilidadeDaFds(
                        (Integer) linha[0],
                        (Integer) linha[1],
                        (Integer) linha[2],
                        Severidade.doCodigo((String) linha[3]),
                        (String) linha[4]))
                .toList();
    }

    /**
     * Só as regras ativas com os dois lados presentes no conjunto: as outras não casam com nenhum par, e a tabela
     * de regras pode crescer sem que cada avaliação a leia inteira.
     */
    private List<RegraDeIncompatibilidade> regrasQuePodemCasar(Set<Integer> substancias, Set<Integer> classes) {
        String lado = classes.isEmpty()
                ? "r.substancia%1$sId in :substancias"
                : "(r.substancia%1$sId in :substancias or r.classe%1$sId in :classes)";
        TypedQuery<IncompatibilidadeRegra> consulta = em.createQuery(
                "select r from IncompatibilidadeRegra r where r.ativo = true and "
                        + lado.formatted("A") + " and " + lado.formatted("B"),
                IncompatibilidadeRegra.class)
                .setParameter("substancias", substancias);
        if (!classes.isEmpty()) {
            consulta.setParameter("classes", classes);
        }
        return consulta.getResultList().stream().map(RegraDeIncompatibilidade::de).toList();
    }

    private Map<Integer, Set<Integer>> classesPorSubstancia(Collection<Integer> substanciaIds) {
        Map<Integer, Set<Integer>> classes = new HashMap<>();
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
}
