package com.api.quimia.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.api.quimia.domain.safety.dto.PrimeirosSocorrosView.PrimeiroSocorroView;
import com.api.quimia.domain.safety.internal.model.LeituraDosPrimeirosSocorros;
import com.api.quimia.domain.safety.internal.model.LeituraDosPrimeirosSocorros.Secao;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/** A seção 4 vem de um JSON externo: o que foge do contrato é descartado, e a leitura nunca lança exceção. */
class LeituraDosPrimeirosSocorrosTest {
    private static final List<String> ROTAS = List.of("inalacao", "pele", "olhos", "ingestao");
    private static final int TEXTO_MAXIMO = 2000;
    private static final int ITENS_MAXIMOS = 20;
    /** O aviso de JSON ilegível é o esperado aqui; milhares deles só poluiriam o log do build. */
    private static final Logger LOG_DA_LEITURA = (Logger) LoggerFactory.getLogger(LeituraDosPrimeirosSocorros.class);
    private static Level nivelOriginal;

    @BeforeAll
    static void silenciarAvisos() {
        nivelOriginal = LOG_DA_LEITURA.getLevel();
        LOG_DA_LEITURA.setLevel(Level.ERROR);
    }

    @AfterAll
    static void restaurarAvisos() {
        LOG_DA_LEITURA.setLevel(nivelOriginal);
    }

    private static final String COMPLETA = """
            {"status":"processado","secoes":{"04":{
              "encontrada":true,
              "primeiros_socorros":[
                {"rota_exposicao":"ingestao","descricao":"  Não induzir vômito. ","sintomas":null,
                 "tratamento_especial":null,"atencao_medica_imediata":true,"evidencias":[]},
                {"rota_exposicao":"inalacao","descricao":"Levar para local ventilado.","sintomas":"Tontura.",
                 "tratamento_especial":"Oxigênio se necessário.","atencao_medica_imediata":false},
                {"rota_exposicao":"olhos","descricao":"Lavar com água por 15 minutos.","atencao_medica_imediata":null},
                {"rota_exposicao":"pele","descricao":"Lavar com água e sabão."}
              ],
              "orientacoes_gerais":[{"descricao":"Mostrar esta ficha ao médico.","evidencias":[]}],
              "notas_ao_medico":[{"descricao":"Tratamento sintomático."}]
            }}}
            """;

    private static String secao04(String corpo) {
        return "{\"secoes\":{\"04\":{\"encontrada\":true," + corpo + "}}}";
    }

    @Test
    void secaoCompletaSaiNaOrdemDasRotasEAparada() {
        Secao secao = LeituraDosPrimeirosSocorros.ler(COMPLETA);

        assertThat(secao.encontrada()).isTrue();
        assertThat(secao.porExposicao()).extracting(PrimeiroSocorroView::rotaExposicao).containsExactlyElementsOf(ROTAS);
        assertThat(secao.porExposicao().get(0))
                .isEqualTo(new PrimeiroSocorroView(
                        "inalacao", "Levar para local ventilado.", "Tontura.", "Oxigênio se necessário.", false));
        assertThat(secao.porExposicao().get(3))
                .isEqualTo(new PrimeiroSocorroView("ingestao", "Não induzir vômito.", null, null, true));
        assertThat(secao.porExposicao().get(2).atencaoMedicaImediata()).isFalse();
        assertThat(secao.orientacoesGerais()).containsExactly("Mostrar esta ficha ao médico.");
        assertThat(secao.notasAoMedico()).containsExactly("Tratamento sintomático.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "", "   ", "null", "[]", "42", "\"texto\"", "{", "{\"secoes\":", "{\"secoes\":[]}", "{\"secoes\":{\"04\":[]}}",
        "{\"secoes\":{\"04\":null}}", "{\"secoes\":{\"03\":{}}}", "{\"secoes\":{\"04\":{\"encontrada\":false,"
                + "\"primeiros_socorros\":[{\"rota_exposicao\":\"pele\",\"descricao\":\"x\"}]}}}",
        "{\"secoes\":{\"04\":{\"encontrada\":\"true\",\"primeiros_socorros\":[{\"rota_exposicao\":\"pele\",\"descricao\":\"x\"}]}}}",
        "{\"secoes\":{\"04\":{\"primeiros_socorros\":[{\"rota_exposicao\":\"pele\",\"descricao\":\"x\"}]}}}",
        "{\"secoes\":{\"04\":{\"encontrada\":true}}} lixo depois"
    })
    void entradaForaDoContratoViraSecaoAusenteSemExcecao(String rawJson) {
        Secao secao = LeituraDosPrimeirosSocorros.ler(rawJson);

        if (rawJson != null && rawJson.endsWith("lixo depois")) {
            assertThat(secao.porExposicao()).isEmpty();
            return;
        }
        assertThat(secao).isEqualTo(Secao.AUSENTE);
    }

    @Test
    void secaoEncontradaSemItensVemVaziaMasEncontrada() {
        Secao secao = LeituraDosPrimeirosSocorros.ler(secao04(
                "\"primeiros_socorros\":{},\"orientacoes_gerais\":\"texto\",\"notas_ao_medico\":null"));

        assertThat(secao.encontrada()).isTrue();
        assertThat(secao.porExposicao()).isEmpty();
        assertThat(secao.orientacoesGerais()).isEmpty();
        assertThat(secao.notasAoMedico()).isEmpty();
    }

    @Test
    void itensForaDoContratoSaoDescartadosUmAUm() {
        Secao secao = LeituraDosPrimeirosSocorros.ler(secao04("""
                "primeiros_socorros":[
                  "pele",
                  {"rota_exposicao":"nariz","descricao":"rota desconhecida"},
                  {"rota_exposicao":"PELE","descricao":"maiúscula não vale"},
                  {"rota_exposicao":7,"descricao":"rota não é texto"},
                  {"descricao":"sem rota"},
                  {"rota_exposicao":"olhos","descricao":"  ","sintomas":42,"tratamento_especial":{"x":1},
                   "atencao_medica_imediata":"true"},
                  {"rota_exposicao":"pele","descricao":"primeiro registro da pele"},
                  {"rota_exposicao":"pele","descricao":"segundo registro da pele"},
                  {"rota_exposicao":"ingestao","atencao_medica_imediata":true}
                ]"""));

        assertThat(secao.porExposicao()).containsExactly(
                new PrimeiroSocorroView("pele", "primeiro registro da pele", null, null, false),
                new PrimeiroSocorroView("ingestao", null, null, null, true));
    }

    @Test
    void itemComUmaSoInformacaoQualquerFica() {
        Secao secao = LeituraDosPrimeirosSocorros.ler(secao04("""
                "primeiros_socorros":[
                  {"rota_exposicao":"inalacao","sintomas":"Tosse."},
                  {"rota_exposicao":"pele","tratamento_especial":"Pomada."},
                  {"rota_exposicao":"olhos","descricao":"Lavar."},
                  {"rota_exposicao":"ingestao","atencao_medica_imediata":true}
                ]"""));

        assertThat(secao.porExposicao()).containsExactly(
                new PrimeiroSocorroView("inalacao", null, "Tosse.", null, false),
                new PrimeiroSocorroView("pele", null, null, "Pomada.", false),
                new PrimeiroSocorroView("olhos", "Lavar.", null, null, false),
                new PrimeiroSocorroView("ingestao", null, null, null, true));
    }

    @Test
    void listaQueChegaComoObjetoNaoEhLida() {
        Secao secao = LeituraDosPrimeirosSocorros.ler(secao04("""
                "primeiros_socorros":{"a":{"rota_exposicao":"pele","descricao":"dentro de objeto"}},
                "orientacoes_gerais":{"a":{"descricao":"dentro de objeto"},"b":"texto solto"},
                "notas_ao_medico":"texto solto\""""));

        assertThat(secao.encontrada()).isTrue();
        assertThat(secao.porExposicao()).isEmpty();
        assertThat(secao.orientacoesGerais()).isEmpty();
        assertThat(secao.notasAoMedico()).isEmpty();
    }

    @Test
    void textosLongosSaoCortadosSemQuebrarEmoji() {
        String longo = "a".repeat(TEXTO_MAXIMO - 1) + "😷" + "fim";
        Secao secao = LeituraDosPrimeirosSocorros.ler(secao04(
                "\"primeiros_socorros\":[{\"rota_exposicao\":\"pele\",\"descricao\":\"" + longo + "\"}],"
                        + "\"orientacoes_gerais\":[\"" + "b".repeat(TEXTO_MAXIMO + 50) + "\"]"));

        String descricao = secao.porExposicao().get(0).descricao();
        assertThat(descricao).hasSize(TEXTO_MAXIMO - 1).isEqualTo("a".repeat(TEXTO_MAXIMO - 1));
        assertThat(secao.orientacoesGerais().get(0)).hasSize(TEXTO_MAXIMO);
    }

    @Test
    void listasDeOrientacaoAceitamObjetoOuTextoEDescartamOResto() {
        Secao secao = LeituraDosPrimeirosSocorros.ler(secao04("""
                "orientacoes_gerais":[{"descricao":" Um "},"Dois",3,null,{"descricao":""},{"outro":"x"},[],"  "],
                "notas_ao_medico":[{"descricao":"Nota"}]"""));

        assertThat(secao.orientacoesGerais()).containsExactly("Um", "Dois");
        assertThat(secao.notasAoMedico()).containsExactly("Nota");
    }

    @Test
    void listasTemTetoDeItens() {
        String muitos = IntStream.range(0, ITENS_MAXIMOS + 15)
                .mapToObj(i -> "{\"descricao\":\"item " + i + "\"}")
                .collect(Collectors.joining(","));

        Secao secao = LeituraDosPrimeirosSocorros.ler(secao04("\"orientacoes_gerais\":[" + muitos + "]"));

        assertThat(secao.orientacoesGerais()).hasSize(ITENS_MAXIMOS).startsWith("item 0").endsWith("item 19");
    }

    @Test
    void aninhamentoProfundoNaoDerrubaALeitura() {
        String profundo = "[".repeat(5000) + "]".repeat(5000);

        assertThat(LeituraDosPrimeirosSocorros.ler("{\"secoes\":{\"04\":" + profundo + "}}")).isEqualTo(Secao.AUSENTE);
    }

    /** Corrompe o JSON completo em posições aleatórias: a leitura nunca lança e a saída respeita o contrato. */
    @Test
    void jsonCorrompidoNuncaLancaESaidaRespeitaOContrato() {
        String[] pedacos = {"", "{", "}", "[", "]", ",", ":", "\"", "null", "true", "\"pele\"", "\"x\"", "9", "\\u0000"};
        for (long semente = 1; semente <= 3000; semente++) {
            SplittableRandom aleatorio = new SplittableRandom(semente);
            StringBuilder json = new StringBuilder(COMPLETA);
            int alteracoes = 1 + aleatorio.nextInt(4);
            for (int i = 0; i < alteracoes; i++) {
                int posicao = aleatorio.nextInt(json.length());
                int fim = Math.min(json.length(), posicao + aleatorio.nextInt(6));
                json.replace(posicao, fim, pedacos[aleatorio.nextInt(pedacos.length)]);
            }

            Secao secao = LeituraDosPrimeirosSocorros.ler(json.toString());

            String contexto = "semente " + semente;
            List<String> rotas = secao.porExposicao().stream().map(PrimeiroSocorroView::rotaExposicao).toList();
            assertThat(rotas).as(contexto).doesNotHaveDuplicates();
            assertThat(ROTAS).as(contexto).containsAll(rotas);
            assertThat(rotas).as(contexto).isSortedAccordingTo((a, b) -> ROTAS.indexOf(a) - ROTAS.indexOf(b));
            for (PrimeiroSocorroView item : secao.porExposicao()) {
                assertThat(item.descricao() != null || item.sintomas() != null || item.tratamentoEspecial() != null
                                || item.atencaoMedicaImediata())
                        .as(contexto)
                        .isTrue();
                for (String texto : new String[] {item.descricao(), item.sintomas(), item.tratamentoEspecial()}) {
                    if (texto != null) {
                        assertThat(texto).as(contexto).isNotBlank().hasSizeLessThanOrEqualTo(TEXTO_MAXIMO);
                    }
                }
            }
            assertThat(secao.orientacoesGerais()).as(contexto).hasSizeLessThanOrEqualTo(ITENS_MAXIMOS).allMatch(t -> !t.isBlank());
            if (!secao.encontrada()) {
                assertThat(secao).as(contexto).isEqualTo(Secao.AUSENTE);
            }
        }
    }
}
