package com.api.quimia.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.domain.safety.dto.AvaliacaoParView;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Compara o motor da API com `fn_match_produtos` do banco real. Só roda no PostgreSQL. A função falhava com
 * `id_regra` ambíguo (G20) até o quimia-bd corrigi-la em 2026-10-27; desde então a paridade vale sem ressalvas.
 */
@SpringBootTest(classes = com.api.quimia.Application.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class CompatibilidadeParidadeTest {
    @Autowired
    private CompatibilidadeGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    private TestCatalogo catalogo;
    private int marca;

    @BeforeEach
    void seed() {
        catalogo = new TestCatalogo(jdbc);
        assumeTrue(catalogo.postgres(), "paridade com fn_match_produtos só existe no PostgreSQL");
        catalogo.limpar();
        marca = catalogo.marca("Marca", null);
    }

    private int produto(String nome, Integer... substancias) {
        int produto = catalogo.produto(nome, marca, "limpeza_geral", null, null);
        int fds = catalogo.fds(produto, "1", LocalDate.of(2026, 1, 1), true, null);
        for (Integer substancia : substancias) {
            catalogo.composto(fds, substancia, "1.00", "2.00", "%");
        }
        return produto;
    }

    private Map<String, Object> doBanco(int a, int b) {
        return jdbc.queryForMap(
                "SELECT resultado, severidade, id_regra FROM fn_match_produtos(?, ?) LIMIT 1", Math.min(a, b), Math.max(a, b));
    }

    @Test
    void incompativelCoincideComAFuncaoDoBanco() {
        int hipoclorito = catalogo.substancia("Hipoclorito de sódio", "7681-52-9", null);
        int amonia = catalogo.substancia("Amônia", "7664-41-7", null);
        catalogo.regra(hipoclorito, null, amonia, null, "media", "Média", true);
        catalogo.regra(hipoclorito, null, amonia, null, "critica", "Crítica", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);

        AvaliacaoParView api = gateway.avaliarPar(a, b);
        Map<String, Object> banco = doBanco(a, b);

        assertThat(api.resultado()).isEqualTo(banco.get("resultado"));
        assertThat(api.severidade()).isEqualTo(banco.get("severidade"));
    }

    /** Desde 2026-10-27 a função do banco também considera o que a seção 10 das FDS declara. */
    @Test
    void incompatibilidadeDeclaradaNaFdsCoincideComAFuncaoDoBanco() {
        int hipoclorito = catalogo.substancia("Hipoclorito de sódio", "7681-52-9", null);
        int amonia = catalogo.substancia("Amônia", "7664-41-7", null);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);
        int fdsA = jdbc.queryForObject("SELECT id FROM fds WHERE id_produto = ?", Integer.class, a);
        catalogo.incompatibilidadeDaFds(fdsA, amonia, null, "alta", "Gases tóxicos");

        AvaliacaoParView api = gateway.avaliarPar(b, a);
        Map<String, Object> banco = doBanco(b, a);

        assertThat(banco.get("resultado")).isEqualTo("incompativel");
        assertThat(api.resultado()).isEqualTo(banco.get("resultado"));
        assertThat(api.severidade()).isEqualTo(banco.get("severidade")).isEqualTo("alta");
        assertThat(api.regraId()).isEqualTo(banco.get("id_regra")).isNull();
    }

    @Test
    void declaradaPelaFdsContraRegraCuradaCoincideComAFuncaoDoBanco() {
        int hipoclorito = catalogo.substancia("Hipoclorito de sódio", "7681-52-9", null);
        int amonia = catalogo.substancia("Amônia", "7664-41-7", null);
        int regra = catalogo.regra(hipoclorito, null, amonia, null, "alta", "Regra curada", true);
        int a = produto("A", hipoclorito);
        int b = produto("B", amonia);
        int fdsB = jdbc.queryForObject("SELECT id FROM fds WHERE id_produto = ?", Integer.class, b);
        catalogo.incompatibilidadeDaFds(fdsB, hipoclorito, null, "alta", "Declarada pela FDS");

        AvaliacaoParView empate = gateway.avaliarPar(a, b);
        Map<String, Object> bancoEmpate = doBanco(a, b);
        assertThat(empate.regraId()).isEqualTo(bancoEmpate.get("id_regra")).isEqualTo(regra);

        catalogo.incompatibilidadeDaFds(fdsB, hipoclorito, null, "critica", "Pior, pela FDS");
        AvaliacaoParView pior = gateway.avaliarPar(a, b);
        Map<String, Object> bancoPior = doBanco(a, b);
        assertThat(pior.severidade()).isEqualTo(bancoPior.get("severidade")).isEqualTo("critica");
        assertThat(pior.regraId()).isEqualTo(bancoPior.get("id_regra")).isNull();
    }

    @Test
    void compativelCoincideComAFuncaoDoBanco() {
        int agua = catalogo.substancia("Água", "7732-18-5", null);
        int sal = catalogo.substancia("Cloreto de sódio", "7647-14-5", null);
        int a = produto("A", agua);
        int b = produto("B", sal);

        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo(doBanco(a, b).get("resultado")).isEqualTo("compativel");
    }

    @Test
    void semFdsCoincideComAFuncaoDoBanco() {
        int agua = catalogo.substancia("Água", "7732-18-5", null);
        int a = catalogo.produto("Sem FDS", marca, "limpeza_geral", null, null);
        int b = produto("B", agua);

        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo(doBanco(a, b).get("resultado")).isEqualTo("nao_avaliado");
    }

    /** Divergência esperada e documentada: o banco devolve `compativel`; a API devolve `nao_avaliado` (R4). */
    @Test
    void composicaoParcialDivergeDeProposito() {
        int agua = catalogo.substancia("Água", "7732-18-5", null);
        int sal = catalogo.substancia("Cloreto de sódio", "7647-14-5", null);
        int a = produto("A", agua);
        int fdsA = jdbc.queryForObject("SELECT id FROM fds WHERE id_produto = ?", Integer.class, a);
        catalogo.composto(fdsA, null, "1.00", "2.00", "%");
        int b = produto("B", sal);

        assertThat(doBanco(a, b).get("resultado")).isEqualTo("compativel");
        assertThat(gateway.avaliarPar(a, b).resultado()).isEqualTo("nao_avaliado");
    }
}
