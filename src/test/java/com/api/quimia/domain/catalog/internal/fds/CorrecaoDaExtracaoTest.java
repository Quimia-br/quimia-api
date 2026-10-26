package com.api.quimia.domain.catalog.internal.fds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.catalog.FakeExtratorFds;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CorrecaoDaExtracaoTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String COM_PENDENCIAS = recurso("/fds/resposta-extrator-com-pendencias.json");

    private static String recurso(String caminho) {
        try (InputStream exemplo = CorrecaoDaExtracaoTest.class.getResourceAsStream(caminho)) {
            return new String(exemplo.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException falha) {
            throw new IllegalStateException(falha);
        }
    }

    private static void recusa(String extracao, List<CorrecaoDeComposto> correcoes) {
        assertThatThrownBy(() -> CorrecaoDaExtracao.aplicar(extracao, correcoes))
                .isInstanceOfSatisfying(AnaliseDeFdsException.class,
                        erro -> assertThat(erro.motivo()).isEqualTo(AnaliseDeFdsException.Motivo.CORRECAO_INVALIDA));
    }

    @Test
    void semCorrecaoDevolveAExtracaoComoVeio() {
        assertThat(CorrecaoDaExtracao.aplicar(COM_PENDENCIAS, List.of())).isSameAs(COM_PENDENCIAS);
    }

    @Test
    void preencheAUnidadeETiraSoAPendenciaQueElaResolve() {
        JsonNode corrigida = JSON.readTree(
                CorrecaoDaExtracao.aplicar(COM_PENDENCIAS, List.of(new CorrecaoDeComposto(0, "  %  "))));

        assertThat(corrigida.at("/secoes/03/compostos/0/unidade_concentracao").asString()).isEqualTo("%");
        assertThat(corrigida.path("pendencias")).hasSize(1);
        assertThat(corrigida.at("/pendencias/0/secao").asString()).isEqualTo("10");
        assertThat(corrigida.path("status").asString()).isEqualTo("processado_com_pendencias");
        assertThat(corrigida.at("/secoes/03/compostos/0/nome").asString()).isEqualTo("Hipoclorito de Sódio");
    }

    @Test
    void semPendenciasRestantesAExtracaoPassaAProcessada() {
        var tree = (tools.jackson.databind.node.ObjectNode) JSON.readTree(COM_PENDENCIAS);
        ((tools.jackson.databind.node.ArrayNode) tree.get("pendencias")).remove(1);

        JsonNode corrigida = JSON.readTree(
                CorrecaoDaExtracao.aplicar(JSON.writeValueAsString(tree), List.of(new CorrecaoDeComposto(0, "%"))));

        assertThat(corrigida.path("pendencias")).isEmpty();
        assertThat(corrigida.path("status").asString()).isEqualTo("processado");
        assertThat(corrigida.path("mensagem").isNull()).isTrue();
    }

    @Test
    void recusaCorrecaoQueNaoCabeNaExtracao() {
        recusa(COM_PENDENCIAS, List.of(new CorrecaoDeComposto(1, "%")));
        recusa(COM_PENDENCIAS, List.of(new CorrecaoDeComposto(-1, "%")));
        recusa(COM_PENDENCIAS, List.of(new CorrecaoDeComposto(0, "%"), new CorrecaoDeComposto(0, "g/L")));
        recusa(COM_PENDENCIAS, List.of(new CorrecaoDeComposto(0, "   ")));
        recusa(COM_PENDENCIAS, List.of(new CorrecaoDeComposto(0, "x".repeat(21))));
        recusa(COM_PENDENCIAS, List.of(new CorrecaoDeComposto(0, "a\u0000b")));
        recusa(COM_PENDENCIAS, List.of(new CorrecaoDeComposto(0, null)));
    }

    @Test
    void naoSobrescreveUnidadeQueODocumentoJaInformou() {
        recusa(FakeExtratorFds.RESPOSTA_DE_EXEMPLO, List.of(new CorrecaoDeComposto(0, "g/L")));
    }

    @Test
    void lerAceitaSoAListaDeObjetosComIndiceEUnidade() {
        assertThat(CorrecaoDeComposto.ler(null)).isEmpty();
        assertThat(CorrecaoDeComposto.ler("  ")).isEmpty();
        assertThat(CorrecaoDeComposto.ler("[{\"indice\":2,\"unidadeConcentracao\":\"%\"}]"))
                .containsExactly(new CorrecaoDeComposto(2, "%"));

        for (String invalido : new String[] {
            "não é json", "{}", "\"%\"", "[1]", "[{}]", "[{\"indice\":\"0\",\"unidadeConcentracao\":\"%\"}]",
            "[{\"indice\":0.5,\"unidadeConcentracao\":\"%\"}]", "[{\"indice\":0,\"unidadeConcentracao\":null}]",
            "[{\"indice\":0,\"unidadeConcentracao\":1}]"
        }) {
            assertThatThrownBy(() -> CorrecaoDeComposto.ler(invalido))
                    .as(invalido)
                    .isInstanceOf(AnaliseDeFdsException.class);
        }
    }
}
