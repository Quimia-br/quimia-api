package com.api.quimia.domain.catalog.internal.usecase;

import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsForm;
import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsResponse;
import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsResponse.CompostoDaFds;
import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsResponse.EvidenciaDaFds;
import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsResponse.PendenciaDaFds;
import com.api.quimia.domain.catalog.internal.dto.AnaliseDeFdsResponse.ReferenciaDoComposto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê a FDS sem cadastrar nada: o portal mostra as pendências, a empresa decide e só então o produto é gravado (com o
 * `analiseId` no cadastro ou na alteração). Fica sem transação, porque o extrator pode levar até um minuto.
 */
@Service
public class AnalisarFdsUseCase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECAO_COMPOSICAO = "03";
    private static final String CAMPO_UNIDADE = "unidade_concentracao";

    private final EmpresaDoPortal portal;
    private final FdsDoProduto fds;

    public AnalisarFdsUseCase(EmpresaDoPortal portal, FdsDoProduto fds) {
        this.portal = portal;
        this.fds = fds;
    }

    public AnaliseDeFdsResponse analisar(Integer empresaId, AnaliseDeFdsForm form) {
        portal.exigirAtiva(empresaId);
        FdsDoProduto.AnaliseAberta analise =
                fds.analisar(empresaId, fds.ler(form.fds()), form.analiseAnterior());
        return resposta(analise);
    }

    /** O PDF da análise abandonada sai do armazenamento. Análise inválida ou de outra empresa não apaga nada. */
    public void descartar(Integer empresaId, String analiseId) {
        portal.exigirAtiva(empresaId);
        fds.descartarAnalise(empresaId, analiseId);
    }

    private static AnaliseDeFdsResponse resposta(FdsDoProduto.AnaliseAberta analise) {
        JsonNode raiz = JSON.readTree(analise.json());
        List<CompostoDaFds> compostos = compostos(raiz.path("secoes").path(SECAO_COMPOSICAO).path("compostos"));
        return new AnaliseDeFdsResponse(
                analise.analiseId(),
                OffsetDateTime.ofInstant(analise.expiraEm(), ZoneOffset.UTC),
                raiz.path("status").asString(),
                texto(raiz.path("mensagem")),
                pendencias(raiz.path("pendencias"), compostos),
                compostos);
    }

    private static List<CompostoDaFds> compostos(JsonNode lista) {
        List<CompostoDaFds> compostos = new ArrayList<>();
        for (JsonNode composto : lista) {
            compostos.add(new CompostoDaFds(
                    compostos.size(),
                    composto.path("nome").asString(),
                    texto(composto.path("cas_numero")),
                    numero(composto.path("concentracao_min")),
                    numero(composto.path("concentracao_max")),
                    texto(composto.path(CAMPO_UNIDADE))));
        }
        return compostos;
    }

    private static List<PendenciaDaFds> pendencias(JsonNode lista, List<CompostoDaFds> compostos) {
        List<PendenciaDaFds> pendencias = new ArrayList<>();
        for (JsonNode pendencia : lista) {
            String secao = pendencia.path("secao").asString();
            String campo = pendencia.path("campo").asString();
            ReferenciaDoComposto composto = referencia(pendencia.path("composto"), compostos);
            pendencias.add(new PendenciaDaFds(
                    secao,
                    campo,
                    pendencia.path("motivo").asString(),
                    pendencia.path("descricao").asString(),
                    composto,
                    corrigivel(secao, campo, composto, compostos),
                    evidencias(pendencia.path("evidencias"))));
        }
        return pendencias;
    }

    /** Só o composto que o extrator apontou e que existe na lista; qualquer outra forma vale como sem referência. */
    private static ReferenciaDoComposto referencia(JsonNode no, List<CompostoDaFds> compostos) {
        if (!no.path("indice").isInt()) {
            return null;
        }
        int indice = no.path("indice").intValue();
        if (indice < 0 || indice >= compostos.size()) {
            return null;
        }
        CompostoDaFds composto = compostos.get(indice);
        return new ReferenciaDoComposto(indice, composto.nome(), composto.casNumero());
    }

    /** A unidade de um composto identificado e ainda sem unidade é o que a empresa pode preencher. */
    private static boolean corrigivel(
            String secao, String campo, ReferenciaDoComposto composto, List<CompostoDaFds> compostos) {
        return SECAO_COMPOSICAO.equals(secao)
                && CAMPO_UNIDADE.equals(campo)
                && composto != null
                && compostos.get(composto.indice()).unidadeConcentracao() == null;
    }

    private static List<EvidenciaDaFds> evidencias(JsonNode lista) {
        List<EvidenciaDaFds> evidencias = new ArrayList<>();
        for (JsonNode evidencia : lista) {
            evidencias.add(new EvidenciaDaFds(
                    evidencia.path("secao").asString(),
                    evidencia.path("pagina").isInt() ? evidencia.path("pagina").intValue() : null,
                    evidencia.path("texto_evidencia").asString()));
        }
        return evidencias;
    }

    private static String texto(JsonNode no) {
        return no.isString() ? no.asString() : null;
    }

    private static BigDecimal numero(JsonNode no) {
        return no.isNumber() ? no.decimalValue() : null;
    }
}
