package com.api.quimia.domain.catalog.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** O nome enviado pelo portal vira o fim da URL de download: seguro, legível e sempre `.pdf`. */
class NomeDoPdfTest {
    @Test
    void mantemONomeLegivelSemAcentoNemEspaco() {
        assertThat(FdsDoProduto.nomeSeguro("Ficha de Segurança – Água Sanitária.pdf"))
                .isEqualTo("Ficha-de-Seguranca-Agua-Sanitaria.pdf");
        assertThat(FdsDoProduto.nomeSeguro("FICHA.PDF")).isEqualTo("FICHA.pdf");
        assertThat(FdsDoProduto.nomeSeguro("ficha")).isEqualTo("ficha.pdf");
    }

    @Test
    void descartaCaminhoECaracteresPerigosos() {
        assertThat(FdsDoProduto.nomeSeguro("C:\\Users\\x\\..\\ficha.pdf")).isEqualTo("ficha.pdf");
        assertThat(FdsDoProduto.nomeSeguro("../../etc/passwd")).isEqualTo("passwd.pdf");
        assertThat(FdsDoProduto.nomeSeguro("a\"b<c>d?.pdf")).isEqualTo("a-b-c-d.pdf");
    }

    @Test
    void semNomeUtilUsaOPadraoELimitaOTamanho() {
        assertThat(FdsDoProduto.nomeSeguro(null)).isEqualTo("fds.pdf");
        assertThat(FdsDoProduto.nomeSeguro("  ")).isEqualTo("fds.pdf");
        assertThat(FdsDoProduto.nomeSeguro("***.pdf")).isEqualTo("fds.pdf");
        assertThat(FdsDoProduto.nomeSeguro("a".repeat(200) + ".pdf")).isEqualTo("a".repeat(80) + ".pdf");
        assertThat(FdsDoProduto.nomeSeguro("a".repeat(79) + " b.pdf")).isEqualTo("a".repeat(79) + ".pdf");
    }
}
