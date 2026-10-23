package com.api.quimia.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.TestCatalogo;
import com.api.quimia.TestJwtKeys;
import com.api.quimia.domain.safety.dto.SubstanciaNaEstanteView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = com.api.quimia.Application.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class SubstanciasDosProdutosTest {
    @Autowired
    private SafetyGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void devolveADescricaoDaSubstanciaOuNulo() {
        TestCatalogo catalogo = new TestCatalogo(jdbc);
        catalogo.limpar();
        int marca = catalogo.marca("Marca", null);
        int produto = catalogo.produto("Produto", marca, "limpeza_geral", null, null);
        int fds = catalogo.fds(produto, "1", LocalDate.of(2026, 1, 1), true, null);
        int comDescricao = catalogo.substancia("Bicarbonato de sódio", "144-55-8", "Sal alcalino suave");
        int semDescricao = catalogo.substancia("Agua", "7732-18-5", null);
        catalogo.composto(fds, comDescricao, "1.00", "2.00", "%");
        catalogo.composto(fds, semDescricao, "1.00", "2.00", "%");

        List<SubstanciaNaEstanteView> substancias = gateway.substanciasDosProdutos(List.of(produto));

        assertThat(substancias).extracting(SubstanciaNaEstanteView::nome)
                .containsExactly("Agua", "Bicarbonato de sódio");
        assertThat(substancias).extracting(SubstanciaNaEstanteView::descricao)
                .containsExactly(null, "Sal alcalino suave");
    }
}
