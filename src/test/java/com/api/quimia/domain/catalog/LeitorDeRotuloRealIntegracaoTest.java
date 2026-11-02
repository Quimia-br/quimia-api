package com.api.quimia.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.catalog.internal.rotulo.CandidatoDoRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.HttpLeitorDeRotulo;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotuloException;
import com.api.quimia.domain.catalog.internal.rotulo.LeitorDeRotuloException.Motivo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * O cliente de rótulo da API contra um quimia-agent de verdade (o `uvicorn` local), com IA real. Fica desligado sem as
 * variáveis de ambiente, para o build não depender de rede nem gastar tokens:
 *
 * <pre>
 *   AGENTE_LOCAL_URL=http://127.0.0.1:8765 ROTULO_FOTO_TESTE=C:/caminho/rotulo.jpg \
 *     ./mvnw -o test -Dtest=LeitorDeRotuloRealIntegracaoTest
 * </pre>
 *
 * A foto de teste é um rótulo com "ÁGUA SANITÁRIA TRADICIONAL", marca "LIMPA BEM" e o código 7891234567895
 * (`scripts/testar_visao.py` do agente gera uma). Usa sockets bloqueantes, como o cliente real.
 */
@EnabledIfEnvironmentVariable(named = "AGENTE_LOCAL_URL", matches = ".+")
class LeitorDeRotuloRealIntegracaoTest {
    private static final Duration PRAZO = Duration.ofSeconds(35);

    private static HttpLeitorDeRotulo leitor() {
        return HttpLeitorDeRotulo.criar(System.getenv("AGENTE_LOCAL_URL"), "", PRAZO);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ROTULO_FOTO_TESTE", matches = ".+")
    void fotoDeRotuloVoltaComNomeMarcaECodigoDeBarras() throws IOException {
        byte[] foto = Files.readAllBytes(Path.of(System.getenv("ROTULO_FOTO_TESTE")));

        List<CandidatoDoRotulo> candidatos = leitor().identificar(foto);

        assertThat(candidatos).isNotEmpty();
        CandidatoDoRotulo primeiro = candidatos.get(0);
        assertThat(primeiro.nome()).containsIgnoringCase("sanit");
        assertThat(primeiro.codBarras()).isEqualTo("7891234567895");
    }

    @Test
    void arquivoQueNaoEhImagemViraFalhaDoAgente() {
        byte[] texto = "não sou uma imagem".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> leitor().identificar(texto)).isInstanceOfSatisfying(
                LeitorDeRotuloException.class, erro -> assertThat(erro.motivo()).isEqualTo(Motivo.FALHOU));
    }
}
