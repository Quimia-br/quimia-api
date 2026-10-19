package com.api.quimia.domain.safety.internal.persistence;

import com.api.quimia.domain.safety.SafetyGateway;
import com.api.quimia.domain.safety.dto.ClasseQuimicaView;
import com.api.quimia.domain.safety.dto.ComposicaoView;
import com.api.quimia.domain.safety.dto.PrimeirosSocorrosView;
import com.api.quimia.domain.safety.dto.ResumoSimplificadoView;
import com.api.quimia.domain.safety.dto.SubstanciaNaEstanteView;
import com.api.quimia.domain.safety.dto.SubstanciaView;
import com.api.quimia.domain.safety.internal.model.ClasseQuimica;
import com.api.quimia.domain.safety.internal.model.Fds;
import com.api.quimia.domain.safety.internal.model.FdsConteudo;
import com.api.quimia.domain.safety.internal.model.LeituraDosPrimeirosSocorros;
import com.api.quimia.domain.safety.internal.model.FdsComposto;
import com.api.quimia.domain.safety.internal.model.Substancia;
import com.api.quimia.domain.safety.internal.model.SubstanciaSinonimo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Consultas somente leitura. A FDS de um produto é sempre a ativa mais recente (data, depois id). */
@Repository
@Transactional(readOnly = true)
public class JpaSafetyGateway implements SafetyGateway {
    @PersistenceContext
    private EntityManager em;

    @Override
    public ResumoSimplificadoView resumoSimplificado(Integer produtoId) {
        Fds fds = fdsAtivasMaisRecentes(List.of(produtoId)).get(produtoId);
        if (fds == null) {
            return new ResumoSimplificadoView(produtoId, null, null, null);
        }
        return new ResumoSimplificadoView(
                produtoId, textoOuNulo(fds.getResumoSimplificado()), fds.getVersao(), fds.getDataAtualizacao());
    }

    @Override
    public PrimeirosSocorrosView primeirosSocorros(Integer produtoId) {
        Fds fds = fdsAtivasMaisRecentes(List.of(produtoId)).get(produtoId);
        if (fds == null) {
            LeituraDosPrimeirosSocorros.Secao vazia = LeituraDosPrimeirosSocorros.Secao.AUSENTE;
            return new PrimeirosSocorrosView(produtoId, false, vazia.porExposicao(), vazia.orientacoesGerais(),
                    vazia.notasAoMedico(), null, null);
        }
        FdsConteudo conteudo = em.find(FdsConteudo.class, fds.getId());
        LeituraDosPrimeirosSocorros.Secao secao =
                LeituraDosPrimeirosSocorros.ler(conteudo == null ? null : conteudo.getRawJson());
        return new PrimeirosSocorrosView(
                produtoId, secao.encontrada(), secao.porExposicao(), secao.orientacoesGerais(), secao.notasAoMedico(),
                fds.getVersao(), fds.getDataAtualizacao());
    }

    @Override
    public List<ComposicaoView> composicao(Integer produtoId) {
        Fds fds = fdsAtivasMaisRecentes(List.of(produtoId)).get(produtoId);
        if (fds == null) {
            return List.of();
        }
        List<Object[]> linhas = compostosComSubstancia(List.of(fds.getId()));
        Map<Integer, List<String>> classes = classesPorSubstancia(substanciaIds(linhas));
        return linhas.stream()
                .map(linha -> {
                    FdsComposto composto = (FdsComposto) linha[0];
                    Substancia substancia = (Substancia) linha[1];
                    return new ComposicaoView(
                            substancia.getId(),
                            substancia.getNomeCanonico(),
                            substancia.getCasNumero(),
                            composto.getConcentracaoMin(),
                            composto.getConcentracaoMax(),
                            composto.getUnidadeConcentracao(),
                            classes.getOrDefault(substancia.getId(), List.of()));
                })
                .toList();
    }

    @Override
    public List<SubstanciaNaEstanteView> substanciasDosProdutos(Collection<Integer> produtoIds) {
        if (produtoIds.isEmpty()) {
            return List.of();
        }
        Map<Integer, Fds> fdsPorProduto = fdsAtivasMaisRecentes(produtoIds);
        Map<Integer, Integer> produtoPorFds = new LinkedHashMap<>();
        fdsPorProduto.forEach((produtoId, fds) -> produtoPorFds.put(fds.getId(), produtoId));
        if (produtoPorFds.isEmpty()) {
            return List.of();
        }
        List<Object[]> linhas = compostosComSubstancia(produtoPorFds.keySet());
        Map<Integer, Substancia> substancias = new LinkedHashMap<>();
        Map<Integer, TreeSet<Integer>> produtosPorSubstancia = new LinkedHashMap<>();
        for (Object[] linha : linhas) {
            Substancia substancia = (Substancia) linha[1];
            Integer produtoId = produtoPorFds.get(((FdsComposto) linha[0]).getFdsId());
            substancias.putIfAbsent(substancia.getId(), substancia);
            produtosPorSubstancia.computeIfAbsent(substancia.getId(), id -> new TreeSet<>()).add(produtoId);
        }
        Map<Integer, List<String>> classes = classesPorSubstancia(substancias.keySet());
        return substancias.values().stream()
                .sorted(Comparator.comparing(Substancia::getNomeCanonico).thenComparing(Substancia::getId))
                .map(substancia -> new SubstanciaNaEstanteView(
                        substancia.getId(),
                        substancia.getNomeCanonico(),
                        substancia.getCasNumero(),
                        substancia.getDescricao(),
                        classes.getOrDefault(substancia.getId(), List.of()),
                        List.copyOf(produtosPorSubstancia.get(substancia.getId()))))
                .toList();
    }

    @Override
    public Optional<SubstanciaView> substancia(Integer substanciaId) {
        Substancia substancia = em.find(Substancia.class, substanciaId);
        if (substancia == null) {
            return Optional.empty();
        }
        List<String> sinonimos = em.createQuery(
                        "select s.sinonimo from SubstanciaSinonimo s where s.substanciaId = :id order by s.sinonimo, s.id",
                        String.class)
                .setParameter("id", substanciaId)
                .getResultList();
        List<ClasseQuimicaView> classes = em.createQuery(
                        """
                        select c from SubstanciaClasseQuimica sc, ClasseQuimica c
                        where c.id = sc.classeQuimicaId and sc.substanciaId = :id
                        order by c.nome, c.id
                        """,
                        ClasseQuimica.class)
                .setParameter("id", substanciaId)
                .getResultList()
                .stream()
                .map(classe -> new ClasseQuimicaView(classe.getId(), classe.getNome(), classe.getDescricao()))
                .toList();
        return Optional.of(new SubstanciaView(
                substancia.getId(),
                substancia.getNomeCanonico(),
                substancia.getCasNumero(),
                substancia.getDescricao(),
                sinonimos,
                classes));
    }

    private Map<Integer, Fds> fdsAtivasMaisRecentes(Collection<Integer> produtoIds) {
        List<Fds> ordenadas = em.createQuery(
                        """
                        select f from Fds f
                        where f.produtoId in :produtos and f.ativo = true
                        order by f.dataAtualizacao desc nulls last, f.id desc
                        """,
                        Fds.class)
                .setParameter("produtos", produtoIds)
                .getResultList();
        Map<Integer, Fds> maisRecente = new LinkedHashMap<>();
        ordenadas.forEach(fds -> maisRecente.putIfAbsent(fds.getProdutoId(), fds));
        return maisRecente;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> compostosComSubstancia(Collection<Integer> fdsIds) {
        return em.createQuery(
                        """
                        select c, s from FdsComposto c, Substancia s
                        where s.id = c.substanciaId and c.fdsId in :fds
                        order by s.nomeCanonico, c.id
                        """)
                .setParameter("fds", fdsIds)
                .getResultList();
    }

    private Map<Integer, List<String>> classesPorSubstancia(Collection<Integer> substanciaIds) {
        Map<Integer, List<String>> classes = new LinkedHashMap<>();
        if (substanciaIds.isEmpty()) {
            return classes;
        }
        List<Object[]> linhas = em.createQuery(
                        """
                        select sc.substanciaId, c.nome from SubstanciaClasseQuimica sc, ClasseQuimica c
                        where c.id = sc.classeQuimicaId and sc.substanciaId in :substancias
                        order by c.nome, c.id
                        """,
                        Object[].class)
                .setParameter("substancias", substanciaIds)
                .getResultList();
        for (Object[] linha : linhas) {
            classes.computeIfAbsent((Integer) linha[0], id -> new ArrayList<>()).add((String) linha[1]);
        }
        return classes;
    }

    private static List<Integer> substanciaIds(List<Object[]> linhas) {
        return linhas.stream().map(linha -> ((Substancia) linha[1]).getId()).distinct().toList();
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
